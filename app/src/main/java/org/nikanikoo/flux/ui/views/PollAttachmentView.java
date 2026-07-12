package org.nikanikoo.flux.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;
import org.nikanikoo.flux.R;
import org.nikanikoo.flux.data.managers.api.OpenVKApi;
import org.nikanikoo.flux.data.models.Poll;
import org.nikanikoo.flux.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PollAttachmentView {
    private static final String TAG = "PollAttachmentView";

    public static void addPollAttachments(Context context, LinearLayout container, List<Poll> polls) {
        if (polls == null || polls.isEmpty()) {
            clearPollAttachments(container);
            return;
        }

        container.removeAllViews();
        container.setVisibility(View.VISIBLE);

        for (Poll poll : polls) {
            View pollView = renderPoll(context, poll, container);
            container.addView(pollView);
        }
    }

    private static View renderPoll(Context context, Poll poll, ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.view_poll_attachment, parent, false);

        TextView questionText = view.findViewById(R.id.poll_question);
        TextView infoText = view.findViewById(R.id.poll_info);
        LinearLayout optionsContainer = view.findViewById(R.id.poll_options_container);
        LinearLayout actionsLayout = view.findViewById(R.id.poll_actions_layout);
        MaterialButton unvoteButton = view.findViewById(R.id.poll_unvote_button);
        MaterialButton submitButton = view.findViewById(R.id.poll_submit_button);
        ImageView menuButton = view.findViewById(R.id.poll_menu_button);

        questionText.setText(poll.getQuestion());

        updateInfoText(context, infoText, poll);

        Set<Integer> selectedOptionIds = new HashSet<>();
        
        final boolean[] isFirstDraw = {true};

        Runnable drawOptions = new Runnable() {
            @Override
            public void run() {
                optionsContainer.removeAllViews();
                boolean hasVoted = poll.getAnswerIds() != null && !poll.getAnswerIds().isEmpty();
                boolean showResults = hasVoted || poll.isClosed() || poll.getCanVote() == 0;
                boolean shouldAnimate = !isFirstDraw[0];
                isFirstDraw[0] = false;

                actionsLayout.setVisibility(View.GONE);
                submitButton.setVisibility(View.GONE);

                boolean canUnvote = !poll.isClosed() && poll.isDisableUnvote() && hasVoted;
                if (canUnvote) {
                    menuButton.setVisibility(View.VISIBLE);
                    menuButton.setOnClickListener(v -> {
                        deleteVote(context, poll, this);
                    });
                } else {
                    menuButton.setVisibility(View.GONE);
                    menuButton.setOnClickListener(null);
                }

                if (showResults) {
                    for (Poll.Answer answer : poll.getAnswers()) {
                        View optionView = inflater.inflate(R.layout.item_poll_option, optionsContainer, false);
                        View progressView = optionView.findViewById(R.id.poll_option_progress);
                        View borderView = optionView.findViewById(R.id.poll_option_border);
                        TextView optionText = optionView.findViewById(R.id.poll_option_text);
                        TextView percentageText = optionView.findViewById(R.id.poll_option_percentage);
                        ImageView optionIcon = optionView.findViewById(R.id.poll_option_icon);

                        optionText.setText(answer.getText());
                        percentageText.setText(String.format(java.util.Locale.US, "%.1f%% (%d)", answer.getRate(), answer.getVotes()));
                        percentageText.setVisibility(View.VISIBLE);

                        boolean userVotedForThis = poll.getAnswerIds().contains(answer.getId());
                        if (userVotedForThis) {
                            borderView.setVisibility(View.VISIBLE);
                            optionIcon.setImageResource(R.drawable.ic_check);
                            optionIcon.setVisibility(View.VISIBLE);
                        } else {
                            borderView.setVisibility(View.GONE);
                            optionIcon.setVisibility(View.GONE);
                        }

                        setProgressWidth(optionView, progressView, answer.getRate(), shouldAnimate);

                        optionView.setClickable(false);
                        optionView.setFocusable(false);

                        optionsContainer.addView(optionView);
                    }
                } else {
                    if (poll.isMultiple()) {
                        actionsLayout.setVisibility(View.VISIBLE);
                        submitButton.setVisibility(View.VISIBLE);
                        submitButton.setEnabled(false);
                        submitButton.setOnClickListener(v -> {
                            if (!selectedOptionIds.isEmpty()) {
                                addVote(context, poll, new ArrayList<>(selectedOptionIds), this);
                            }
                        });
                    }

                    for (Poll.Answer answer : poll.getAnswers()) {
                        View optionView = inflater.inflate(R.layout.item_poll_option, optionsContainer, false);
                        TextView optionText = optionView.findViewById(R.id.poll_option_text);
                        ImageView optionIcon = optionView.findViewById(R.id.poll_option_icon);

                        optionText.setText(answer.getText());
                        optionIcon.setVisibility(View.VISIBLE);

                        if (poll.isMultiple()) {
                            optionIcon.setImageResource(selectedOptionIds.contains(answer.getId()) 
                                    ? R.drawable.ic_check 
                                    : R.drawable.ic_add);
                        } else {
                            optionIcon.setImageResource(R.drawable.ic_add);
                        }

                        optionView.setOnClickListener(v -> {
                            if (poll.isMultiple()) {
                                if (selectedOptionIds.contains(answer.getId())) {
                                    selectedOptionIds.remove(answer.getId());
                                    optionIcon.setImageResource(R.drawable.ic_add);
                                } else {
                                    selectedOptionIds.add(answer.getId());
                                    optionIcon.setImageResource(R.drawable.ic_check);
                                }
                                submitButton.setEnabled(!selectedOptionIds.isEmpty());
                            } else {
                                List<Integer> singleVote = new ArrayList<>();
                                singleVote.add(answer.getId());
                                addVote(context, poll, singleVote, this);
                            }
                        });

                        optionsContainer.addView(optionView);
                    }
                }
                updateInfoText(context, infoText, poll);
            }
        };

        drawOptions.run();
        return view;
    }

    private static void updateInfoText(Context context, TextView infoText, Poll poll) {
        StringBuilder info = new StringBuilder();
        if (poll.isClosed()) {
            info.append(context.getString(R.string.poll_closed));
        } else {
            info.append(poll.isMultiple() ? context.getString(R.string.poll_multiple) : context.getString(R.string.poll_single));
        }
        info.append(" • ");
        info.append(poll.getVotesCount() > 0 
                ? String.format(context.getString(R.string.poll_votes_count), poll.getVotesCount())
                : context.getString(R.string.poll_votes_count_0));

        infoText.setText(info.toString());
    }

    private static void addVote(Context context, Poll poll, List<Integer> answerIds, Runnable onSuccessRedraw) {
        OpenVKApi api = OpenVKApi.getInstance(context);
        Map<String, String> params = new HashMap<>();
        params.put("poll_id", String.valueOf(poll.getId()));
        params.put("owner_id", String.valueOf(poll.getOwnerId()));
        
        StringBuilder answersStr = new StringBuilder();
        for (int i = 0; i < answerIds.size(); i++) {
            if (i > 0) answersStr.append(",");
            answersStr.append(answerIds.get(i));
        }
        params.put("answer_ids", answersStr.toString());
        params.put("answers_ids", answersStr.toString());

        final List<Integer> prevAnswerIds = new ArrayList<>(poll.getAnswerIds());
        final int prevVotesCount = poll.getVotesCount();
        final int prevCanVote = poll.getCanVote();
        final List<Poll.Answer> prevAnswers = new ArrayList<>();
        for (Poll.Answer ans : poll.getAnswers()) {
            prevAnswers.add(new Poll.Answer(ans.getId(), ans.getText(), ans.getVotes(), ans.getRate()));
        }

        poll.setAnswerIds(answerIds);
        poll.setCanVote(0);
        
        int totalVotes = 0;
        for (Poll.Answer ans : poll.getAnswers()) {
            if (answerIds.contains(ans.getId())) {
                ans.setVotes(ans.getVotes() + 1);
            }
            totalVotes += ans.getVotes();
        }
        poll.setVotesCount(totalVotes);
        for (Poll.Answer ans : poll.getAnswers()) {
            ans.setRate(totalVotes > 0 ? (ans.getVotes() * 100.0 / totalVotes) : 0.0);
        }

        onSuccessRedraw.run();

        api.callMethod("polls.addVote", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                Logger.d(TAG, "Vote added successfully on server: " + response.toString());
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error voting on server: " + error);
                if (context != null) {
                    android.os.Handler handler = new android.os.Handler(context.getMainLooper());
                    handler.post(() -> {
                        Toast.makeText(context, context.getString(R.string.poll_error_vote) + ": " + error, Toast.LENGTH_SHORT).show();
                        poll.setAnswerIds(prevAnswerIds);
                        poll.setVotesCount(prevVotesCount);
                        poll.setCanVote(prevCanVote);
                        poll.setAnswers(prevAnswers);
                        onSuccessRedraw.run();
                    });
                }
            }
        });
    }

    private static void deleteVote(Context context, Poll poll, Runnable onSuccessRedraw) {
        OpenVKApi api = OpenVKApi.getInstance(context);
        Map<String, String> params = new HashMap<>();
        params.put("poll_id", String.valueOf(poll.getId()));
        params.put("owner_id", String.valueOf(poll.getOwnerId()));

        final List<Integer> prevAnswerIds = new ArrayList<>(poll.getAnswerIds());
        final int prevVotesCount = poll.getVotesCount();
        final int prevCanVote = poll.getCanVote();
        final List<Poll.Answer> prevAnswers = new ArrayList<>();
        for (Poll.Answer ans : poll.getAnswers()) {
            prevAnswers.add(new Poll.Answer(ans.getId(), ans.getText(), ans.getVotes(), ans.getRate()));
        }

        poll.setAnswerIds(new ArrayList<>());
        poll.setCanVote(1);
        
        int totalVotes = 0;
        for (Poll.Answer ans : poll.getAnswers()) {
            if (prevAnswerIds.contains(ans.getId())) {
                ans.setVotes(Math.max(0, ans.getVotes() - 1));
            }
            totalVotes += ans.getVotes();
        }
        poll.setVotesCount(totalVotes);
        for (Poll.Answer ans : poll.getAnswers()) {
            ans.setRate(totalVotes > 0 ? (ans.getVotes() * 100.0 / totalVotes) : 0.0);
        }

        onSuccessRedraw.run();

        api.callMethod("polls.deleteVote", params, new OpenVKApi.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                Logger.d(TAG, "Vote deleted successfully on server: " + response.toString());
            }

            @Override
            public void onError(String error) {
                Logger.e(TAG, "Error retracting vote on server: " + error);
                if (context != null) {
                    android.os.Handler handler = new android.os.Handler(context.getMainLooper());
                    handler.post(() -> {
                        Toast.makeText(context, context.getString(R.string.poll_error_unvote) + ": " + error, Toast.LENGTH_SHORT).show();
                        poll.setAnswerIds(prevAnswerIds);
                        poll.setVotesCount(prevVotesCount);
                        poll.setCanVote(prevCanVote);
                        poll.setAnswers(prevAnswers);
                        onSuccessRedraw.run();
                    });
                }
            }
        });
    }

    private static void setProgressWidth(View optionView, View progressView, double rate, boolean animate) {
        progressView.setVisibility(View.VISIBLE);
        
        Runnable setWidthRunnable = () -> {
            int totalWidth = optionView.getWidth();
            if (totalWidth <= 0) return;
            
            int targetWidth = (int) (totalWidth * (rate / 100.0));
            if (animate) {
                ValueAnimator animator = ValueAnimator.ofInt(0, targetWidth);
                animator.setDuration(400);
                animator.setInterpolator(new DecelerateInterpolator());
                animator.addUpdateListener(animation -> {
                    int val = (int) animation.getAnimatedValue();
                    ViewGroup.LayoutParams lp = progressView.getLayoutParams();
                    lp.width = val;
                    progressView.setLayoutParams(lp);
                });
                animator.start();
            } else {
                ViewGroup.LayoutParams lp = progressView.getLayoutParams();
                lp.width = targetWidth;
                progressView.setLayoutParams(lp);
            }
        };

        if (optionView.getWidth() > 0) {
            setWidthRunnable.run();
        } else {
            optionView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    optionView.removeOnLayoutChangeListener(this);
                    setWidthRunnable.run();
                }
            });
        }
    }

    public static void clearPollAttachments(LinearLayout container) {
        if (container != null) {
            container.removeAllViews();
            container.setVisibility(View.GONE);
        }
    }
}
