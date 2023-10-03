package org.commcare.commcaresupportlibrary.identity.model;

import android.os.Parcel;
import android.os.Parcelable;

import org.commcare.commcaresupportlibrary.identity.BiometricIdentifier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class RegistrationResult implements Parcelable {
    private String guid;
    private Map<BiometricIdentifier, byte[]> templates;

    /**
     * Result of the identity enrollment workflow
     *
     * @param guid Global unique id generated by the Identity Provder as part of the registration/enrollment workflow
     */
    public RegistrationResult(String guid) {
        this.guid = guid;
        this.templates = new HashMap<>(0);
    }

    public RegistrationResult(String guid, Map<BiometricIdentifier, byte[]> templates) {
        this.guid = guid;
        this.templates = templates;
    }

    protected RegistrationResult(Parcel in) {
        guid = in.readString();
        int numTemplates = in.readInt();
        templates = new HashMap<>(numTemplates);
        for (int i=0;i < numTemplates; i++){
            BiometricIdentifier biometricIdentifier = BiometricIdentifier.values()[in.readInt()];
            int templateSize = in.readInt();
            byte[] template = new byte[templateSize];
            in.readByteArray(template);
            templates.put(biometricIdentifier, template);
        }
    }

    public static final Creator<RegistrationResult> CREATOR = new Creator<RegistrationResult>() {
        @Override
        public RegistrationResult createFromParcel(Parcel in) {
            return new RegistrationResult(in);
        }

        @Override
        public RegistrationResult[] newArray(int size) {
            return new RegistrationResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(guid);
        dest.writeInt(getNumberOfTemplates());
        for (Map.Entry<BiometricIdentifier, byte[]> template : templates.entrySet()){
            dest.writeInt(template.getKey().ordinal());
            dest.writeInt(template.getValue().length);
            dest.writeByteArray(template.getValue());
        }
    }

    public String getGuid() {
        return guid;
    }

    public Map<BiometricIdentifier, byte[]> getTemplates() {
        return templates;
    }

    public int getNumberOfTemplates() {
        return templates.size();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RegistrationResult)) {
            return false;
        }
        RegistrationResult other = (RegistrationResult)o;
        if (!guid.equals(other.guid)) {
            return false;
        }
        if (getNumberOfTemplates() != other.getNumberOfTemplates()){
            return false;
        }

        for (Map.Entry<BiometricIdentifier, byte[]> template : templates.entrySet()){
            byte[] otherTemplate = other.getTemplates().get(template.getKey());
            if (!Arrays.equals(template.getValue(), otherTemplate)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = guid.hashCode();
        for (Map.Entry<BiometricIdentifier, byte[]> template : templates.entrySet()){
            hash += Arrays.hashCode(template.getValue());
        }
        return hash;
    }
}
