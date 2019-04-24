package com.cab.mycab.historyRecyclerView;

public class HistoryObject {


    private String rideId;

    // this is the constructor
    public HistoryObject (String rideId){
        this.rideId = rideId;
    }

    // with this we can retrieve the data
    public String getRideId() {
        return rideId;
    }

}
