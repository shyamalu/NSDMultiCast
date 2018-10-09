package org.chimple.flores.db.entity;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;


public class SyncRequestMessageDeserializer implements JsonDeserializer<SyncInfoRequestMessage> {

    public SyncInfoRequestMessage deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
            throws JsonParseException {

        final JsonObject jsonObject = json.getAsJsonObject();

        final JsonElement jsonMessageType = jsonObject.get("mt");
        String messageType = "";
        if (jsonMessageType != null) {
            messageType = jsonMessageType.getAsString();
        }

        final JsonElement jsonFrom = jsonObject.get("md");
        String from = "";
        if (jsonFrom != null) {
            from = jsonFrom.getAsString();
        }


        SyncInfoItem[] infos = context.deserialize(jsonObject.get("items"), SyncInfoItem[].class);
        final SyncInfoRequestMessage message = new SyncInfoRequestMessage(from, new ArrayList(Arrays.asList(infos)));
        return message;
    }
}