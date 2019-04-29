package com.cab.mycab.historyRecyclerView;

public class HistoryObject {


    private String rideId;
    private String time;

    // this is the constructor
    public HistoryObject (String rideId, String time){
        this.rideId = rideId;
        this.time = time;
    }


    // with this we can retrieve the data
    public String getRideId() {
        return rideId;
    }
    public void setRideId(String rideId){
        this.rideId = rideId;
    }
    public String getTime() {
        return time;
    }
    public void setTime(String time) {
        this.time = time;
    }

}
