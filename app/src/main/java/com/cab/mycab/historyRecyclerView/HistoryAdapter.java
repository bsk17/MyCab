package com.cab.mycab.historyRecyclerView;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.cab.mycab.R;

import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryViewHolder>  {
    // this list will hold the items of the tye HistoryObject
    private List<HistoryObject> itemList;
    private Context context;


    public HistoryAdapter(List<HistoryObject> itemList, Context context) {
        this.itemList = itemList;
        this.context = context;
    }

    @Override
    public HistoryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        // we specify the layout file file for the recycler view
        View layoutView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, null, false);

        // we set the layout height and width
        RecyclerView.LayoutParams lp =
                new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutView.setLayoutParams(lp);
        HistoryViewHolder rcv = new HistoryViewHolder(layoutView);

        return rcv;
    }

    @Override
    public void onBindViewHolder(HistoryViewHolder holder, int position) {
        // the rideId below is from the ViewHolder Class
        holder.rideId.setText(itemList.get(position).getRideId());
    }

    @Override
    public int getItemCount() {
        return 0;
    }
}
