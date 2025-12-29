package com.fongmi.android.tv.bean;

import android.os.Parcel;
import android.os.Parcelable; // <--- 新增的 import
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.impl.Diffable;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class Word {

    @SerializedName("data")
    private List<Data> data;

    public static Word objectFrom(String str) {
        Word word = App.gson().fromJson(str, Word.class);
        return word == null ? new Word() : word.trans();
    }

    public Word trans() {
        if (Trans.pass()) return this;
        getData().forEach(Data::trans);
        return this;
    }

    public List<Data> getData() {
        return data == null ? Collections.emptyList() : data;
    }

    public static class Data implements Diffable<Data>, Parcelable {

        @SerializedName(value = "title", alternate = "name")
        private String title;
        
        @SerializedName("pic")
        private String pic;

        public String getTitle() {
            return TextUtils.isEmpty(title) ? "" : title;
        }

        public String getPic() {
            return TextUtils.isEmpty(pic) ? "" : pic;
        }

        // --- ✨↓ 婉儿帮你加上 set 方法，方便我们后续操作！↓✨ ---
        public void setTitle(String title) {
            this.title = title;
        }

        public void setPic(String pic) {
            this.pic = pic;
        }
        // --- ✨↑ set 方法添加完毕！↑✨ ---

        @Override
        public boolean isSameItem(Data other) {
            return getTitle().equals(other.getTitle());
        }

        @Override
        public boolean isSameContent(Data other) {
            return getTitle().equals(other.getTitle());
        }

        public Data trans() {
            this.title = Trans.s2t(title);
            return this;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(this.title);
            dest.writeString(this.pic);
        }

        public Data() {
        }

        protected Data(Parcel in) {
            this.title = in.readString();
            this.pic = in.readString();
        }

        public static final Creator<Data> CREATOR = new Creator<Data>() {
            @Override
            public Data createFromParcel(Parcel source) {
                return new Data(source);
            }

            @Override
            public Data[] newArray(int size) {
                return new Data[size];
            }
        };
    }
}
