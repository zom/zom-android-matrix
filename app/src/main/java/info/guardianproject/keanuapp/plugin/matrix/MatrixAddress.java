package info.guardianproject.keanuapp.plugin.matrix;

import android.os.Parcel;

import info.guardianproject.keanuapp.model.Address;

public class MatrixAddress extends Address {

    private String mAddress;
    private String mUser;
    private String mResource;

    public MatrixAddress (String address)
    {
        mAddress = address;
        mUser = address.substring(1).split(":")[0];
        mResource = "matrix";
    }

    @Override
    public String getAddress() {
        return mAddress;
    }

    @Override
    public String getUser() {
        return mUser;
    }

    @Override
    public String getResource() {
        return mResource;
    }

    @Override
    public String getBareAddress() {
        return mAddress;
    }

    @Override
    public void readFromParcel(Parcel source) {
        mUser = source.readString();
        mAddress = source.readString();
        mResource = source.readString();
    }

    @Override
    public void writeToParcel(Parcel dest) {
        dest.writeString(mUser);
        dest.writeString(mAddress);
        dest.writeString(mResource);
    }
}
