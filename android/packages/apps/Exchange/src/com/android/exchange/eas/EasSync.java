/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.exchange.eas;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.support.v4.util.LongSparseArray;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.android.emailcommon.provider.Account;
import com.android.emailcommon.provider.EmailContent;
import com.android.emailcommon.provider.Mailbox;
import com.android.emailcommon.provider.MessageStateChange;
import com.android.exchange.CommandStatusException;
import com.android.exchange.Eas;
import com.android.exchange.EasResponse;
import com.android.exchange.adapter.EmailSyncParser;
import com.android.exchange.adapter.Parser;
import com.android.exchange.adapter.Serializer;
import com.android.exchange.adapter.Tags;
import com.android.mail.utils.LogUtils;

import org.apache.http.HttpEntity;

import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Performs an Exchange Sync operation for one {@link Mailbox}.
 * TODO: For now, only handles upsync.
 * TODO: Handle multiple folders in one request. Not sure if parser can handle it yet.
 */
public class EasSync extends EasOperation {

    // TODO: When we handle downsync, this will become relevant.
    private boolean mInitialSync;

    // State for the mailbox we're currently syncing.
    private long mMailboxId;
    private String mMailboxServerId;
    private String mMailboxSyncKey;
    private List<MessageStateChange> mStateChanges;
    private Map<String, Integer> mMessageUpdateStatus;

    public EasSync(final Context context, final Account account) {
        super(context, account);
        mInitialSync = false;
    }

    private long getMessageId(final String serverId) {
        // TODO: Improve this.
        for (final MessageStateChange change : mStateChanges) {
            if (change.getServerId().equals(serverId)) {
                return change.getMessageId();
            }
        }
        return EmailContent.Message.NO_MESSAGE;
    }

    private void handleMessageUpdateStatus(final Map<String, Integer> messageStatus,
            final long[][] messageIds, final int[] counts) {
        for (final Map.Entry<String, Integer> entry : messageStatus.entrySet()) {
            final String serverId = entry.getKey();
            final int status = entry.getValue();
            final int index;
            if (EmailSyncParser.shouldRetry(status)) {
                index = 1;
            } else {
                index = 0;
            }
            final long messageId = getMessageId(serverId);
            if (messageId != EmailContent.Message.NO_MESSAGE) {
                messageIds[index][counts[index]] = messageId;
                ++counts[index];
            }
        }
    }

    /**
     * TODO: return value doesn't do what it claims.
     * @return Number of messages successfully synced, or -1 if we encountered an error.
     */
    public final int upsync(final SyncResult syncResult) {
        final List<MessageStateChange> changes = MessageStateChange.getChanges(mContext, mAccountId,
                        getProtocolVersion() < Eas.SUPPORTED_PROTOCOL_EX2007_DOUBLE);
        if (changes == null) {
            return 0;
        }
        final LongSparseArray<List<MessageStateChange>> allData =
                MessageStateChange.convertToChangesMap(changes);
        if (allData == null) {
            return 0;
        }

        final long[][] messageIds = new long[2][changes.size()];
        final int[] counts = new int[2];

        for (int i = 0; i < allData.size(); ++i) {
            mMailboxId = allData.keyAt(i);
            mStateChanges = allData.valueAt(i);
            final Cursor mailboxCursor = mContext.getContentResolver().query(
                    ContentUris.withAppendedId(Mailbox.CONTENT_URI, mMailboxId),
                    Mailbox.ProjectionSyncData.PROJECTION, null, null, null);
            if (mailboxCursor != null) {
                try {
                    if (mailboxCursor.moveToFirst()) {
                        mMailboxServerId = mailboxCursor.getString(
                                Mailbox.ProjectionSyncData.COLUMN_SERVER_ID);
                        mMailboxSyncKey = mailboxCursor.getString(
                                Mailbox.ProjectionSyncData.COLUMN_SYNC_KEY);
                        final int result;
                        if (TextUtils.isEmpty(mMailboxSyncKey) || mMailboxSyncKey.equals("0")) {
                            // For some reason we can get here without a valid mailbox sync key
                            // b/10797675
                            // TODO: figure out why and clean this up
                            LogUtils.d(LOG_TAG,
                                    "Tried to sync mailbox %d with invalid mailbox sync key",
                                    mMailboxId);
                            result = -1;
                        } else {
                            result = performOperation(syncResult);
                        }
                        if (result == 0) {
                            handleMessageUpdateStatus(mMessageUpdateStatus, messageIds, counts);
                        } else {
                            for (final MessageStateChange msc : mStateChanges) {
                                messageIds[1][counts[1]] = msc.getMessageId();
                                ++counts[1];
                            }
                        }
                    }
                } finally {
                    mailboxCursor.close();
                }
            }
        }

        final ContentResolver cr = mContext.getContentResolver();
        MessageStateChange.upsyncSuccessful(cr, messageIds[0], counts[0]);
        MessageStateChange.upsyncRetry(cr, messageIds[1], counts[1]);

        return 0;
    }

    @Override
    protected String getCommand() {
        return "Sync";
    }

    @Override
    protected HttpEntity getRequestEntity() throws IOException {
        final Serializer s = new Serializer();
        s.start(Tags.SYNC_SYNC);
        s.start(Tags.SYNC_COLLECTIONS);
        addOneCollectionToRequest(s, Mailbox.TYPE_MAIL, mMailboxServerId, mMailboxSyncKey,
                mStateChanges);
        s.end().end().done();
        return makeEntity(s);
    }

    @Override
    protected int handleResponse(final EasResponse response, final SyncResult syncResult)
            throws IOException {
        final Account account = Account.restoreAccountWithId(mContext, mAccountId);
        if (account == null) {
            // TODO: Make this some other error type, since the account is just gone now.
            return RESULT_OTHER_FAILURE;
        }
        final Mailbox mailbox = Mailbox.restoreMailboxWithId(mContext, mMailboxId);
        if (mailbox == null) {
            return RESULT_OTHER_FAILURE;
        }
        final EmailSyncParser parser = new EmailSyncParser(mContext, mContext.getContentResolver(),
                response.getInputStream(), mailbox, account);
        try {
            parser.parse();
            mMessageUpdateStatus = parser.getMessageStatuses();
        } catch (final Parser.EmptyStreamException e) {
            // This indicates a compressed response which was empty, which is OK.
        } catch (final CommandStatusException e) {
            // TODO: This is the wrong error type.
            return RESULT_OTHER_FAILURE;
        }
        return 0;
    }

    @Override
    protected long getTimeout() {
        if (mInitialSync) {
            return 120 * DateUtils.SECOND_IN_MILLIS;
        }
        return super.getTimeout();
    }

    /**
     * Create date/time in RFC8601 format.  Oddly enough, for calendar date/time, Microsoft uses
     * a different format that excludes the punctuation (this is why I'm not putting this in a
     * parent class)
     */
    private static String formatDateTime(final Calendar calendar) {
        final StringBuilder sb = new StringBuilder();
        //YYYY-MM-DDTHH:MM:SS.MSSZ
        sb.append(calendar.get(Calendar.YEAR));
        sb.append('-');
        sb.append(String.format(Locale.US, "%02d", calendar.get(Calendar.MONTH) + 1));
        sb.append('-');
        sb.append(String.format(Locale.US, "%02d", calendar.get(Calendar.DAY_OF_MONTH)));
        sb.append('T');
        sb.append(String.format(Locale.US, "%02d", calendar.get(Calendar.HOUR_OF_DAY)));
        sb.append(':');
        sb.append(String.format(Locale.US, "%02d", calendar.get(Calendar.MINUTE)));
        sb.append(':');
        sb.append(String.format(Locale.US, "%02d", calendar.get(Calendar.SECOND)));
        sb.append(".000Z");
        return sb.toString();
    }

    private final void addOneCollectionToRequest(final Serializer s, final int collectionType,
            final String mailboxServerId, final String mailboxSyncKey,
            final List<MessageStateChange> stateChanges) throws IOException {

        s.start(Tags.SYNC_COLLECTION);
        if (getProtocolVersion() < Eas.SUPPORTED_PROTOCOL_EX2007_SP1_DOUBLE) {
            s.data(Tags.SYNC_CLASS, Eas.getFolderClass(collectionType));
        }
        s.data(Tags.SYNC_SYNC_KEY, mailboxSyncKey);
        s.data(Tags.SYNC_COLLECTION_ID, mailboxServerId);
        s.data(Tags.SYNC_GET_CHANGES, "0");
        s.start(Tags.SYNC_COMMANDS);
        for (final MessageStateChange change : stateChanges) {
            s.start(Tags.SYNC_CHANGE);
            s.data(Tags.SYNC_SERVER_ID, change.getServerId());
            s.start(Tags.SYNC_APPLICATION_DATA);
            final int newFlagRead = change.getNewFlagRead();
            if (newFlagRead != MessageStateChange.VALUE_UNCHANGED) {
                s.data(Tags.EMAIL_READ, Integer.toString(newFlagRead));
            }
            final int newFlagFavorite = change.getNewFlagFavorite();
            if (newFlagFavorite != MessageStateChange.VALUE_UNCHANGED) {
                // "Flag" is a relatively complex concept in EAS 12.0 and above.  It is not only
                // the boolean "favorite" that we think of in Gmail, but it also represents a
                // follow up action, which can include a subject, start and due dates, and even
                // recurrences.  We don't support any of this as yet, but EAS 12.0 and higher
                // require that a flag contain a status, a type, and four date fields, two each
                // for start date and end (due) date.
                if (newFlagFavorite != 0) {
                    // Status 2 = set flag
                    s.start(Tags.EMAIL_FLAG).data(Tags.EMAIL_FLAG_STATUS, "2");
                    // "FollowUp" is the standard type
                    s.data(Tags.EMAIL_FLAG_TYPE, "FollowUp");
                    final long now = System.currentTimeMillis();
                    final Calendar calendar =
                            GregorianCalendar.getInstance(TimeZone.getTimeZone("GMT"));
                    calendar.setTimeInMillis(now);
                    // Flags are required to have a start date and end date (duplicated)
                    // First, we'll set the current date/time in GMT as the start time
                    String utc = formatDateTime(calendar);
                    s.data(Tags.TASK_START_DATE, utc).data(Tags.TASK_UTC_START_DATE, utc);
                    // And then we'll use one week from today for completion date
                    calendar.setTimeInMillis(now + DateUtils.WEEK_IN_MILLIS);
                    utc = formatDateTime(calendar);
                    s.data(Tags.TASK_DUE_DATE, utc).data(Tags.TASK_UTC_DUE_DATE, utc);
                    s.end();
                } else {
                    s.tag(Tags.EMAIL_FLAG);
                }
            }
            s.end().end();  // SYNC_APPLICATION_DATA, SYNC_CHANGE
        }
        s.end().end();  // SYNC_COMMANDS, SYNC_COLLECTION
    }
}
