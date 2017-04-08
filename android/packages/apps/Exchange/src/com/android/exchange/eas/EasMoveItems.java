package com.android.exchange.eas;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;

import com.android.emailcommon.provider.Account;
import com.android.emailcommon.provider.EmailContent;
import com.android.emailcommon.provider.MessageMove;
import com.android.exchange.EasResponse;
import com.android.exchange.adapter.MoveItemsParser;
import com.android.exchange.adapter.Serializer;
import com.android.exchange.adapter.Tags;
import com.android.mail.utils.LogUtils;

import org.apache.http.HttpEntity;

import java.io.IOException;
import java.util.List;

/**
 * Performs a MoveItems request, which is used to move items between collections.
 * See http://msdn.microsoft.com/en-us/library/ee160102(v=exchg.80).aspx for more details.
 * TODO: Investigate how this interacts with ItemOperations.
 */
public class EasMoveItems extends EasOperation {

    /** Result code indicating that no moved messages were found for this account. */
    public final static int RESULT_NO_MESSAGES = 0;
    public final static int RESULT_OK = 1;
    public final static int RESULT_EMPTY_RESPONSE = 2;

    private static class MoveResponse {
        public final String sourceMessageId;
        public final String newMessageId;
        public final int moveStatus;

        public MoveResponse(final String srcMsgId, final String dstMsgId, final int status) {
            sourceMessageId = srcMsgId;
            newMessageId = dstMsgId;
            moveStatus = status;
        }
    }

    private MessageMove mMove;
    private MoveResponse mResponse;

    public EasMoveItems(final Context context, final Account account) {
        super(context, account);
    }

    // TODO: Allow multiple messages in one request. Requires parser changes.
    public int upsyncMovedMessages(final SyncResult syncResult) {
        final List<MessageMove> moves = MessageMove.getMoves(mContext, mAccountId);
        if (moves == null) {
            return RESULT_NO_MESSAGES;
        }

        final long[][] messageIds = new long[3][moves.size()];
        final int[] counts = new int[3];

        for (final MessageMove move : moves) {
            mMove = move;
            final int result = performOperation(syncResult);
            final int status;
            if (result == RESULT_OK) {
                processResponse(mMove, mResponse);
                status = mResponse.moveStatus;
            } else {
                // TODO: Perhaps not all errors should be retried?
                // Notably, if the server returns 200 with an empty response, we retry. This is
                // how the previous version worked, and I can't find documentation about what this
                // response state really means.
                status = MoveItemsParser.STATUS_CODE_RETRY;
            }
            final int index = status - 1;
            messageIds[index][counts[index]] = mMove.getMessageId();
            ++counts[index];
        }

        final ContentResolver cr = mContext.getContentResolver();
        MessageMove.upsyncSuccessful(cr, messageIds[0], counts[0]);
        MessageMove.upsyncFail(cr, messageIds[1], counts[1]);
        MessageMove.upsyncRetry(cr, messageIds[2], counts[2]);

        return RESULT_OK;
    }

    @Override
    protected String getCommand() {
        return "MoveItems";
    }

    @Override
    protected HttpEntity getRequestEntity() throws IOException {
        final Serializer s = new Serializer();
        s.start(Tags.MOVE_MOVE_ITEMS);
        s.start(Tags.MOVE_MOVE);
        s.data(Tags.MOVE_SRCMSGID, mMove.getServerId());
        s.data(Tags.MOVE_SRCFLDID, mMove.getSourceFolderId());
        s.data(Tags.MOVE_DSTFLDID, mMove.getDestFolderId());
        s.end();
        s.end().done();
        return makeEntity(s);
    }

    @Override
    protected int handleResponse(final EasResponse response, final SyncResult syncResult)
            throws IOException {
        if (!response.isEmpty()) {
            final MoveItemsParser parser = new MoveItemsParser(response.getInputStream());
            parser.parse();
            final String sourceMessageId = parser.getSourceServerId();
            final String newMessageId = parser.getNewServerId();
            final int status = parser.getStatusCode();
            mResponse = new MoveResponse(sourceMessageId, newMessageId, status);
            return RESULT_OK;
        }
        return RESULT_EMPTY_RESPONSE;
    }

    private void processResponse(final MessageMove request, final MoveResponse response) {
        // TODO: Eventually this should use a transaction.
        // TODO: Improve how the parser reports statuses and how we handle them here.
        if (!response.sourceMessageId.equals(request.getServerId())) {
            // TODO: This is bad, but I think we need to respect the response anyway.
            LogUtils.e(LOG_TAG, "Got a response for a message we didn't request");
        }

        final ContentValues cv = new ContentValues(1);
        if (response.moveStatus == MoveItemsParser.STATUS_CODE_REVERT) {
            // Restore the old mailbox id
            cv.put(EmailContent.MessageColumns.MAILBOX_KEY, request.getSourceFolderKey());
        } else if (response.moveStatus == MoveItemsParser.STATUS_CODE_SUCCESS) {
            if (response.newMessageId != null
                    && !response.newMessageId.equals(response.sourceMessageId)) {
                cv.put(EmailContent.SyncColumns.SERVER_ID, response.newMessageId);
            }
        }
        if (cv.size() != 0) {
            mContext.getContentResolver().update(
                    ContentUris.withAppendedId(EmailContent.Message.CONTENT_URI,
                            request.getMessageId()), cv, null, null);
        }
    }
}
