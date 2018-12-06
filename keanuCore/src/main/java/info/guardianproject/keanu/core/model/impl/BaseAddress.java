package info.guardianproject.keanu.core.model.impl;

import android.os.Parcel;

import info.guardianproject.keanu.core.model.Address;

public class BaseAddress extends Address {

    public String mAddress;

    public BaseAddress (String address)
    {
        mAddress = address;
    }

    @Override
    public String getAddress() {
        return mAddress;
    }

    @Override
    public String getUser() {
        return mAddress;
    }

    @Override
    public String getResource() {
        return null;
    }

    @Override
    public String getBareAddress() {
        return mAddress;
    }

    @Override
    public void readFromParcel(Parcel source) {
        mAddress = source.readString();
    }

    @Override
    public void writeToParcel(Parcel dest) {
        dest.writeString(mAddress);
    }
}
