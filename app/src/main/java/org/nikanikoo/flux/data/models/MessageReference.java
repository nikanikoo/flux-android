package org.nikanikoo.flux.data.models;

public class MessageReference {
    private final int id;
    private final String sender;
    private final String text;

    public MessageReference(int id, String sender, String text) {
        this.id = id;
        this.sender = sender;
        this.text = text;
    }

    public int getId() { return id; }
    public String getSender() { return sender; }
    public String getText() { return text; }
}
