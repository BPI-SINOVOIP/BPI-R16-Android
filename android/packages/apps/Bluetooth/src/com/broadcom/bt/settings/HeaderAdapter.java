/*******************************************************************************
 *
 *  Copyright (C) 2012-2013 Broadcom Corporation
 *
 *  This program is the proprietary software of Broadcom Corporation and/or its
 *  licensors, and may only be used, duplicated, modified or distributed
 *  pursuant to the terms and conditions of a separate, written license
 *  agreement executed between you and Broadcom (an "Authorized License").
 *  Except as set forth in an Authorized License, Broadcom grants no license
 *  (express or implied), right to use, or waiver of any kind with respect to
 *  the Software, and Broadcom expressly reserves all rights in and to the
 *  Software and all intellectual property rights therein.
 *  IF YOU HAVE NO AUTHORIZED LICENSE, THEN YOU HAVE NO RIGHT TO USE THIS
 *  SOFTWARE IN ANY WAY, AND SHOULD IMMEDIATELY NOTIFY BROADCOM AND DISCONTINUE
 *  ALL USE OF THE SOFTWARE.
 *
 *  Except as expressly set forth in the Authorized License,
 *
 *  1.     This program, including its structure, sequence and organization,
 *         constitutes the valuable trade secrets of Broadcom, and you shall
 *         use all reasonable efforts to protect the confidentiality thereof,
 *         and to use this information only in connection with your use of
 *         Broadcom integrated circuit products.
 *
 *  2.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE SOFTWARE IS PROVIDED
 *         "AS IS" AND WITH ALL FAULTS AND BROADCOM MAKES NO PROMISES,
 *         REPRESENTATIONS OR WARRANTIES, EITHER EXPRESS, IMPLIED, STATUTORY,
 *         OR OTHERWISE, WITH RESPECT TO THE SOFTWARE.  BROADCOM SPECIFICALLY
 *         DISCLAIMS ANY AND ALL IMPLIED WARRANTIES OF TITLE, MERCHANTABILITY,
 *         NONINFRINGEMENT, FITNESS FOR A PARTICULAR PURPOSE, LACK OF VIRUSES,
 *         ACCURACY OR COMPLETENESS, QUIET ENJOYMENT, QUIET POSSESSION OR
 *         CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING OUT
 *         OF USE OR PERFORMANCE OF THE SOFTWARE.
 *
 *  3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM OR
 *         ITS LICENSORS BE LIABLE FOR
 *         (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 *               DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 *               YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 *               HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 *         (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 *               SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 *               LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 *               ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 *
 *******************************************************************************/
package com.broadcom.bt.settings;

import java.util.List;

import android.content.Context;
import android.preference.PreferenceActivity.Header;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.android.bluetooth.R;

public abstract class HeaderAdapter extends ArrayAdapter<Header> implements
        CompoundButton.OnCheckedChangeListener {
    private static final String TAG="BtSettings.HeaderAdapter";
    static final int HEADER_TYPE_CATEGORY = 0;
    static final int HEADER_TYPE_NORMAL = 1;
    static final int HEADER_TYPE_SWITCH = 2;
    private static final int HEADER_TYPE_COUNT = HEADER_TYPE_SWITCH + 1;

    public static interface OnCheckedChangeListener {
        public void onCheckedChanged(int position, boolean isChecked);
    }

    public static class HeaderViewHolder {
        public ImageView icon;
        public TextView title;
        public TextView summary;
        public Switch switch_;
    }

    private LayoutInflater mInflater;


    private SparseArray<HeaderViewHolder> mHeaderViews =
                          new SparseArray<HeaderAdapter.HeaderViewHolder>();

    public HeaderViewHolder getHeaderViewHolder(int pos) {
        return mHeaderViews.get(pos);
    }

    protected void setHeaderViewHolder(int pos, HeaderViewHolder h) {
        mHeaderViews.put(pos, h);
    }

    protected abstract int getHeaderType(Header header);

    protected abstract int getHeaderLayoutResId(int headerType);

    protected abstract boolean getSwitchState(Header header);

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Log.d(TAG, "onCheckChanged: isChecked = " + isChecked);
        Log.d(TAG, "view=" + buttonView);

        if (mCheckedChangeListener != null) {
            Integer switchPosition = (Integer) buttonView.getTag();
            Log.d(TAG, "SwitchPosition = " + switchPosition);
            if (switchPosition != null) {
                mCheckedChangeListener.onCheckedChanged(switchPosition, isChecked);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        Header header = getItem(position);
        return getHeaderType(header);
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false; // because of categories
    }

    @Override
    public boolean isEnabled(int position) {
        return getItemViewType(position) != HEADER_TYPE_CATEGORY;
    }

    @Override
    public int getViewTypeCount() {
        return HEADER_TYPE_COUNT;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    private OnCheckedChangeListener mCheckedChangeListener;

    public void setCheckedChangeListener(OnCheckedChangeListener listener) {
        mCheckedChangeListener = listener;
    }

    public HeaderAdapter(Context context, List<Header> objects) {
        super(context, 0, objects);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        Header header = getItem(position);
        int headerType = getHeaderType(header);
        View view = null;
        Log.d(TAG, "getView(): position = " + position + ", convertView = "
                + convertView);

        HeaderViewHolder holder = getHeaderViewHolder(position);
        if (holder == null) {
            holder = new HeaderViewHolder();
            setHeaderViewHolder(position, holder);
        }

        if (convertView == null) {
            switch (headerType) {
            case HEADER_TYPE_CATEGORY:
                view = new TextView(getContext(), null, android.R.attr.listSeparatorTextViewStyle);
                break;
            case HEADER_TYPE_SWITCH:
                view = mInflater.inflate(getHeaderLayoutResId(headerType), parent, false);
                break;
            case HEADER_TYPE_NORMAL:
                view = mInflater.inflate(getHeaderLayoutResId(headerType), parent, false);
                break;
            }
        } else {
            view = convertView;
        }

        Log.d(TAG, "view=" + view);
        // All view fields must be updated every time, because the view may be
        // recycled
        switch (headerType) {
        case HEADER_TYPE_CATEGORY:
            holder.title = (TextView) view;
            holder.title.setText(header.getTitle(getContext().getResources()));
            break;

        case HEADER_TYPE_SWITCH:
            holder.switch_ = (Switch) view.findViewById(R.id.switchWidget);
            // No break, fall through on purpose to update common fields
            boolean isChecked = getSwitchState(header);
            holder.switch_.setOnCheckedChangeListener(null);
            holder.switch_.setTag(position);
            holder.switch_.setChecked(isChecked);
            holder.switch_.setOnCheckedChangeListener(this);
            //$FALL-THROUGH$
        case HEADER_TYPE_NORMAL:
            // holder.icon = (ImageView) view.findViewById(R.id.icon);
            holder.title = (TextView) view.findViewById(com.android.internal.R.id.title);
            holder.summary = (TextView) view.findViewById(com.android.internal.R.id.summary);
            // holder.icon.setImageResource(header.iconRes);
            holder.title.setText(header.getTitle(getContext().getResources()));
            CharSequence summary = header.getSummary(getContext().getResources());
            if (!TextUtils.isEmpty(summary)) {
                holder.summary.setVisibility(View.VISIBLE);
                holder.summary.setText(summary);
            } else {
                holder.summary.setVisibility(View.GONE);
            }
            break;
        }
        return view;
    }

}
