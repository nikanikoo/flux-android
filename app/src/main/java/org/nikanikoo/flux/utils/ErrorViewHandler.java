package org.nikanikoo.flux.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.nikanikoo.flux.R;

public class ErrorViewHandler {

    public enum ErrorType {
        NO_INTERNET,
        API_ERROR,
        TIMEOUT,
        UNAUTHORIZED,
        GENERIC,
        EMPTY_NOTES,
        EMPTY_SEARCH,
        EMPTY_STATE,
        EMPTY_CHAT
    }

    private View errorView;
    private View mainContent;
    private TextView errorTitle;
    private TextView errorMessage;
    private android.widget.ImageView errorImage;
    private View retryButton;
    private RetryCallback retryCallback;

    public interface RetryCallback {
        void onRetry();
    }

    public ErrorViewHandler(@NonNull Context context, @NonNull ViewGroup parent, @NonNull View mainContent) {
        this.mainContent = mainContent;
        init(context, parent);
    }

    public ErrorViewHandler(@NonNull Context context, @NonNull View errorView) {
        this.errorView = errorView;
        initViews();
    }

    private void init(@NonNull Context context, @NonNull ViewGroup parent) {
        errorView = LayoutInflater.from(context).inflate(R.layout.view_error_state, parent, false);
        initViews();
        parent.addView(errorView);
    }

    private void initViews() {
        errorTitle = errorView.findViewById(R.id.error_title);
        errorMessage = errorView.findViewById(R.id.error_message);
        errorImage = errorView.findViewById(R.id.error_image);
        retryButton = errorView.findViewById(R.id.error_retry_button);

        if (retryButton != null) {
            retryButton.setOnClickListener(v -> {
                if (retryCallback != null) {
                    retryCallback.onRetry();
                }
            });
        }
    }

    public void showError(@NonNull ErrorType type) {
        int titleRes = R.string.error_generic_title;
        int messageRes = R.string.error_generic_message;
        int imageRes = R.drawable.veselcraft;
        boolean showButton = true;
        int buttonTextRes = R.string.error_retry;

        switch (type) {
            case NO_INTERNET:
                titleRes = R.string.error_no_internet_title;
                messageRes = R.string.error_no_internet_message;
                imageRes = R.drawable.nikanikoo;
                break;
            case TIMEOUT:
                titleRes = R.string.error_timeout_title;
                messageRes = R.string.error_timeout_message;
                imageRes = R.drawable.nikanikoo;
                break;
            case API_ERROR:
                titleRes = R.string.error_api_title;
                messageRes = R.string.error_api_message;
                imageRes = R.drawable.veselcraft;
                break;
            case UNAUTHORIZED:
                titleRes = R.string.error_unauthorized_title;
                messageRes = R.string.error_unauthorized_message;
                imageRes = R.drawable.veselcraft;
                break;
            case EMPTY_NOTES:
                titleRes = R.string.empty_notes_title;
                messageRes = R.string.empty_notes_desc;
                imageRes = R.drawable.abobus228;
                buttonTextRes = R.string.note_btn_create_note;
                break;
            case EMPTY_SEARCH:
                titleRes = R.string.empty_search_title;
                messageRes = R.string.empty_search_desc;
                imageRes = R.drawable.daniel_myslivets;
                showButton = false;
                break;
            case EMPTY_STATE:
                titleRes = R.string.empty_state_title;
                messageRes = R.string.empty_state_desc;
                imageRes = R.drawable.konata;
                showButton = false;
                break;
            case EMPTY_CHAT:
                titleRes = R.string.empty_chat_title;
                messageRes = R.string.empty_chat_desc;
                imageRes = R.drawable.vepur;
                showButton = false;
                break;
            case GENERIC:
            default:
                titleRes = R.string.error_generic_title;
                messageRes = R.string.error_generic_message;
                imageRes = R.drawable.veselcraft;
                break;
        }

        if (errorTitle != null) {
            errorTitle.setVisibility(View.VISIBLE);
            errorTitle.setText(titleRes);
        }
        if (errorMessage != null) {
            errorMessage.setVisibility(View.VISIBLE);
            errorMessage.setText(messageRes);
        }
        if (errorImage != null) {
            boolean showArts = ThemeManager.getInstance(errorView.getContext()).isShowArts();
            if (showArts) {
                errorImage.setVisibility(View.VISIBLE);
                errorImage.setImageResource(imageRes);
                errorImage.setImageTintList(null);
            } else {
                errorImage.setVisibility(View.GONE);
            }
        }
        if (retryButton != null) {
            retryButton.setVisibility(showButton ? View.VISIBLE : View.GONE);
            if (showButton && retryButton instanceof android.widget.Button) {
                ((android.widget.Button) retryButton).setText(buttonTextRes);
            }
        }
        setVisible(true);
    }

    public void showError(@StringRes int titleRes, @StringRes int messageRes) {
        if (errorTitle != null) {
            errorTitle.setVisibility(View.VISIBLE);
            errorTitle.setText(titleRes);
        }
        if (errorMessage != null) {
            errorMessage.setVisibility(View.VISIBLE);
            errorMessage.setText(messageRes);
        }
        if (errorImage != null) {
            boolean showArts = ThemeManager.getInstance(errorView.getContext()).isShowArts();
            if (showArts) {
                errorImage.setVisibility(View.VISIBLE);
                Context context = errorView.getContext();
                String msg = context.getString(messageRes).toLowerCase();
                if (msg.contains("сеть") || msg.contains("интернет") || msg.contains("connection") || msg.contains("network")) {
                    errorImage.setImageResource(R.drawable.nikanikoo);
                } else {
                    errorImage.setImageResource(R.drawable.veselcraft);
                }
                errorImage.setImageTintList(null);
            } else {
                errorImage.setVisibility(View.GONE);
            }
        }
        if (retryButton != null) {
            retryButton.setVisibility(View.VISIBLE);
            if (retryButton instanceof android.widget.Button) {
                ((android.widget.Button) retryButton).setText(R.string.error_retry);
            }
        }
        setVisible(true);
    }

    public void showError(@NonNull String title, @NonNull String message) {
        if (errorTitle != null) {
            errorTitle.setVisibility(View.VISIBLE);
            errorTitle.setText(title);
        }
        if (errorMessage != null) {
            errorMessage.setVisibility(View.VISIBLE);
            errorMessage.setText(message);
        }
        if (errorImage != null) {
            boolean showArts = ThemeManager.getInstance(errorView.getContext()).isShowArts();
            if (showArts) {
                errorImage.setVisibility(View.VISIBLE);
                String msg = message.toLowerCase();
                if (msg.contains("сеть") || msg.contains("интернет") || msg.contains("connection") || msg.contains("network")) {
                    errorImage.setImageResource(R.drawable.nikanikoo);
                } else {
                    errorImage.setImageResource(R.drawable.veselcraft);
                }
                errorImage.setImageTintList(null);
            } else {
                errorImage.setVisibility(View.GONE);
            }
        }
        if (retryButton != null) {
            retryButton.setVisibility(View.VISIBLE);
            if (retryButton instanceof android.widget.Button) {
                ((android.widget.Button) retryButton).setText(R.string.error_retry);
            }
        }
        setVisible(true);
    }

    public void showErrorWithTitle(@StringRes int titleRes) {
        if (errorTitle != null) {
            errorTitle.setVisibility(View.VISIBLE);
            errorTitle.setText(titleRes);
        }
        if (errorMessage != null) {
            errorMessage.setVisibility(View.GONE);
        }
        if (errorImage != null) {
            boolean showArts = ThemeManager.getInstance(errorView.getContext()).isShowArts();
            if (showArts) {
                errorImage.setVisibility(View.VISIBLE);
                errorImage.setImageResource(R.drawable.veselcraft);
                errorImage.setImageTintList(null);
            } else {
                errorImage.setVisibility(View.GONE);
            }
        }
        if (retryButton != null) {
            retryButton.setVisibility(View.VISIBLE);
            if (retryButton instanceof android.widget.Button) {
                ((android.widget.Button) retryButton).setText(R.string.error_retry);
            }
        }
        setVisible(true);
    }

    public void showErrorWithMessage(@StringRes int messageRes) {
        if (errorTitle != null) {
            errorTitle.setVisibility(View.GONE);
        }
        if (errorMessage != null) {
            errorMessage.setVisibility(View.VISIBLE);
            errorMessage.setText(messageRes);
        }
        if (errorImage != null) {
            boolean showArts = ThemeManager.getInstance(errorView.getContext()).isShowArts();
            if (showArts) {
                errorImage.setVisibility(View.VISIBLE);
                errorImage.setImageResource(R.drawable.veselcraft);
                errorImage.setImageTintList(null);
            } else {
                errorImage.setVisibility(View.GONE);
            }
        }
        if (retryButton != null) {
            retryButton.setVisibility(View.VISIBLE);
            if (retryButton instanceof android.widget.Button) {
                ((android.widget.Button) retryButton).setText(R.string.error_retry);
            }
        }
        setVisible(true);
    }

    public void hideError() {
        setVisible(false);
        if (mainContent != null) {
            mainContent.setVisibility(View.VISIBLE);
        }
    }

    public void setRetryCallback(RetryCallback callback) {
        this.retryCallback = callback;
    }

    public boolean isErrorVisible() {
        return errorView != null && errorView.getVisibility() == View.VISIBLE;
    }

    private void setVisible(boolean visible) {
        if (errorView != null) {
            errorView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (mainContent != null) {
            mainContent.setVisibility(visible ? View.GONE : View.VISIBLE);
        }
    }

    public View getErrorView() {
        return errorView;
    }

    public static boolean isNetworkError(String error) {
        if (error == null) return false;
        String lower = error.toLowerCase();
        return lower.contains("network")
                || lower.contains("connection")
                || lower.contains("timeout")
                || lower.contains("socket")
                || lower.contains("unreachable")
                || lower.contains("нет подключения")
                || lower.contains("ошибка сети");
    }

    public static ErrorType detectErrorType(String error) {
        if (error == null) return ErrorType.GENERIC;
        String lower = error.toLowerCase();

        if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("token")) {
            return ErrorType.UNAUTHORIZED;
        }
        if (lower.contains("timeout")) {
            return ErrorType.TIMEOUT;
        }
        if (lower.contains("500") || lower.contains("502") || lower.contains("503") || lower.contains("504")) {
            return ErrorType.API_ERROR;
        }
        if (isNetworkError(error)) {
            return ErrorType.NO_INTERNET;
        }
        return ErrorType.GENERIC;
    }

    public void showErrorAuto(@NonNull String errorMessage) {
        ErrorType type = detectErrorType(errorMessage);
        showError(type);
        if (this.errorMessage != null) {
            this.errorMessage.setVisibility(View.VISIBLE);
            this.errorMessage.setText(errorMessage);
        }
    }
}
