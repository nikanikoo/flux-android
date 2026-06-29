package org.nikanikoo.flux.data.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Poll implements Serializable {
    private int id;
    private int ownerId;
    private String question;
    private int votesCount;
    private boolean multiple;
    private boolean closed;
    private int canVote;
    private boolean disableUnvote;
    private List<Integer> answerIds = new ArrayList<>();
    private List<Answer> answers = new ArrayList<>();

    public static class Answer implements Serializable {
        private int id;
        private String text;
        private int votes;
        private double rate;

        public Answer(int id, String text, int votes, double rate) {
            this.id = id;
            this.text = text;
            this.votes = votes;
            this.rate = rate;
        }

        public int getId() { return id; }
        public String getText() { return text; }
        public int getVotes() { return votes; }
        public double getRate() { return rate; }

        public void setVotes(int votes) { this.votes = votes; }
        public void setRate(double rate) { this.rate = rate; }
    }

    public Poll() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOwnerId() { return ownerId; }
    public void setOwnerId(int ownerId) { this.ownerId = ownerId; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public int getVotesCount() { return votesCount; }
    public void setVotesCount(int votesCount) { this.votesCount = votesCount; }

    public boolean isMultiple() { return multiple; }
    public void setMultiple(boolean multiple) { this.multiple = multiple; }

    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }

    public int getCanVote() { return canVote; }
    public void setCanVote(int canVote) { this.canVote = canVote; }

    public boolean isDisableUnvote() { return disableUnvote; }
    public void setDisableUnvote(boolean disableUnvote) { this.disableUnvote = disableUnvote; }

    public List<Integer> getAnswerIds() { return answerIds; }
    public void setAnswerIds(List<Integer> answerIds) { this.answerIds = answerIds; }

    public List<Answer> getAnswers() { return answers; }
    public void setAnswers(List<Answer> answers) { this.answers = answers; }
}
