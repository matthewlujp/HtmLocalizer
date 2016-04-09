package com.example.luning.htmlocalizer;


import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by luning on 2016/04/09.
 */
public class DrawerListAdapter extends RecyclerView.Adapter<DrawerListAdapter.ItemViewHolder> {
    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView mIcon;
        TextView mTitle, mText;

        public ItemViewHolder(View itemView, int viewType) {
            super(itemView);
            mTitle = (TextView) itemView.findViewById(R.id.item_name);
            mIcon = (ImageView) itemView.findViewById(R.id.item_icon);
        }
    }

    private ArrayList<SiteListItem> mItems;

    public DrawerListAdapter(ArrayList<SiteListItem> items) {
        mItems = items;
    }

    @Override
    public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_row, parent, false);
        ItemViewHolder holder = new ItemViewHolder(view, viewType);
        return holder;
    }

    @Override
    public void onBindViewHolder(ItemViewHolder holder, int position) {
        holder.mTitle.setText(mItems.get(position).getTitle());
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public SiteListItem getItem(int position) {
        return mItems.get(position);
    }
}
