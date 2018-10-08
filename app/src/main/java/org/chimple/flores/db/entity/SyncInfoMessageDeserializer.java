package org.chimple.flores.db.entity;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;


public class SyncInfoMessageDeserializer implements JsonDeserializer<SyncInfoMessage> {

    public SyncInfoMessage deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
            throws JsonParseException {

        final JsonObject jsonObject = json.getAsJsonObject();

        final JsonElement jsonMessageType = jsonObject.get("message_type");
        String messageType = "";
        if (jsonMessageType != null) {
            messageType = jsonMessageType.getAsString();
        }


        P2PSyncInfo[] infos = context.deserialize(jsonObject.get("infos"), P2PSyncInfo[].class);
        final SyncInfoMessage message = new SyncInfoMessage(messageType, new ArrayList(Arrays.asList(infos)));
        return message;
    }
}