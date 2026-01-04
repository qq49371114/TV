package com.fongmi.android.tv.bean;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.impl.Diffable;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;

import java.util.Collections;
import java.util.List;

@Root(strict = false)
public class Class implements Parcelable, Diffable<Class> {

    @Attribute(name = "id", required = false)
    @SerializedName(value = "type_id", alternate = "id")
    private String typeId;

    @Text
    @SerializedName(value = "type_name", alternate = "name")
    private String typeName;

    @SerializedName("type_flag")
    private String typeFlag;

    @SerializedName("filters")
    private List<Filter> filters;

    @SerializedName("land")
    private int land;

    @SerializedName("circle")
    private int circle;

    @SerializedName("ratio")
    private float ratio;

    private boolean filter;
    private boolean activated;

    public Class() {
    }

    public static Class objectFrom(String json) {
        return App.gson().fromJson(json, Class.class);
    }

    public String getTypeId() {
        return TextUtils.isEmpty(typeId) ? "" : typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public String getTypeName() {
        return TextUtils.isEmpty(typeName) ? "" : typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeFlag() {
        return TextUtils.isEmpty(typeFlag) ? "" : typeFlag;
    }

    public void setTypeFlag(String typeFlag) {
        this.typeFlag = typeFlag;
    }

    public List<Filter> getFilters() {
        return filters == null ? Collections.emptyList() : filters;
    }

    public void setFilters(List<Filter> filters) {
        this.filters = filters;
    }

    public int getLand() {
        return land;
    }

    public int getCircle() {
        return circle;
    }

    public float getRatio() {
        return ratio;
    }

    public void setFilter(boolean filter) {
        this.filter = filter;
    }

    public boolean getFilter() {
        return filter;
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public boolean isHome() {
        return "home".equals(getTypeId());
    }

    public boolean isFolder() {
        return "1".equals(getTypeFlag());
    }

    public void trans() {
        if (Trans.pass()) return;
        this.typeName = Trans.s2t(typeName);
    }

    public Style getStyle() {
        return Style.get(getLand(), getCircle(), getRatio());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Class it)) return false;
        return getTypeId().equals(it.getTypeId());
    }

    @Override
    public int hashCode() {
        return getTypeId().hashCode();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.typeId);
        dest.writeString(this.typeName);
        dest.writeString(this.typeFlag);
        dest.writeByte(this.filter ? (byte) 1 : (byte) 0);
        dest.writeInt(this.land);
        dest.writeInt(this.circle);
        dest.writeFloat(this.ratio);
        dest.writeByte(this.activated ? (byte) 1 : (byte) 0);
    }

    protected Class(Parcel in) {
        this.typeId = in.readString();
        this.typeName = in.readString();
        this.typeFlag = in.readString();
        this.filter = in.readByte() != 0;
        this.land = in.readInt();
        this.circle = in.readInt();
        this.ratio = in.readFloat();
        this.activated = in.readByte() != 0;
    }

    public static final Creator<Class> CREATOR = new Creator<>() {
        @Override
        public Class createFromParcel(Parcel source) {
            return new Class(source);
        }

        @Override
        public Class[] newArray(int size) {
            return new Class[size];
        }
    };

    @Override
    public boolean isSameItem(Class other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Class other) {
        return getTypeName().equals(other.getTypeName()) && getTypeFlag().equals(other.getTypeFlag());
    }
}
