package com.cab.mycab.historyRecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;

import com.cab.mycab.HistorySingleActivity;
import com.cab.mycab.R;

public class HistoryViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

    public TextView rideId;
    public TextView time;

    public HistoryViewHolder(View itemView) {
        super(itemView);
        itemView.setOnClickListener(this);

        rideId = itemView.findViewById(R.id.rideId);
        time = itemView.findViewById(R.id.time);
    }

    // when we click a rideId then we can go to the specific details of that ride with onClick
    @Override
    public void onClick(View v) {
        //Bundle is generally used for passing data between various activities of android.
        Intent intent = new Intent(v.getContext(), HistorySingleActivity.class);
        Bundle b = new Bundle();
        b.putString("rideId", rideId.getText().toString());
        intent.putExtras(b);
        v.getContext().startActivity(intent);
    }
}
