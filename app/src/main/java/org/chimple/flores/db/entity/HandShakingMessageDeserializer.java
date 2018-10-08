package org.chimple.flores.db.entity;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.ArrayList;


public class HandShakingMessageDeserializer implements JsonDeserializer<HandShakingMessage> {

    public HandShakingMessage deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
            throws JsonParseException {

        final JsonObject jsonObject = json.getAsJsonObject();

        final JsonElement jsonMessageType = jsonObject.get("message_type");
        String messageType = "";
        if (jsonMessageType != null) {
            messageType = jsonMessageType.getAsString();
        }

        final JsonElement jsonFrom = jsonObject.get("from");
        String from = "";
        if (jsonFrom != null) {
            from = jsonFrom.getAsString();
        }

        HandShakingInfo[] infos = context.deserialize(jsonObject.get("infos"), HandShakingInfo[].class);
        final HandShakingMessage handShakingMessage = new HandShakingMessage(from, messageType, new ArrayList(Arrays.asList(infos)));
        return handShakingMessage;
    }
}