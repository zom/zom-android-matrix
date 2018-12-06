package info.guardianproject.keanu.matrix.plugin;

import android.os.Parcel;

import info.guardianproject.keanu.core.model.Address;
import info.guardianproject.keanu.core.model.impl.BaseAddress;


public class MatrixAddress extends BaseAddress {

    private String mUser;
    private String mResource;

    public MatrixAddress (String address)
    {

        super (address);

        if (address.startsWith("@")) {
            mUser = address.substring(1).split(":")[0];

        }
        else if (address.startsWith("!")) {
            mUser = address.split(":")[0];
        }
        else
        {
            //parts from XMPP style address
            String[] parts = address.split("@");
            mUser = parts[0];
            mAddress = '@' + mUser + ':' + parts[1];
        }

        mResource = "matrix";
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
