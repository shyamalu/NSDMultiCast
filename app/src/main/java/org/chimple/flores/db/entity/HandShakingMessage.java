package org.chimple.flores.db.entity;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import org.chimple.flores.application.P2PApplication;

import java.util.List;

public class HandShakingMessage {
    @Expose(serialize = true, deserialize = true)
    @SerializedName("message_type")
    String messageType;

    @Expose(serialize = true, deserialize = true)
    @SerializedName("from")
    String from;

    public String getFrom() { return from; }

    public String getMessageType() {
        return messageType;
    }

    public List<HandShakingInfo> getInfos() {
        return infos;
    }

    @Expose(serialize = true, deserialize = true)
    @SerializedName("infos")
    List<HandShakingInfo> infos;

    public HandShakingMessage(String from, String messageType, List<HandShakingInfo> infos) {
        this.messageType = messageType;
        this.infos = infos;
        this.from = from;
    }


    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        final HandShakingMessage info = (HandShakingMessage) obj;
        if (this == info) {
            return true;
        } else {
            return (this.messageType.equals(info.messageType) && this.from.equals(info.from) && this.infos == info.infos);
        }
    }


    public int hashCode() {
        int hashno = 7;
        hashno = 13 * hashno + from.hashCode() +(messageType == null ? 0 : messageType.hashCode()) + (infos == null ? 0 : infos.hashCode());
        return hashno;
    }
}
