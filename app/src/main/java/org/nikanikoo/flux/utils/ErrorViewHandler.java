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
        GENERIC
    }

    private View errorView;
    private View mainContent;
    private TextView errorTitle;
    private TextView errorMessage;
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
        switch (type) {
            case NO_INTERNET:
                showError(R.string.error_no_internet_title, R.string.error_no_internet_message);
                break;
            case API_ERROR:
                showError(R.string.error_api_title, R.string.error_api_message);
                break;
            case TIMEOUT:
                showError(R.string.error_timeout_title, R.string.error_timeout_message);
                break;
            case UNAUTHORIZED:
                showError(R.string.error_unauthorized_title, R.string.error_unauthorized_message);
                break;
            case GENERIC:
            default:
                showError(R.string.error_generic_title, R.string.error_generic_message);
                break;
        }
    }

    public void showError(@StringRes int titleRes, @StringRes int messageRes) {
        if (errorTitle != null) {
            errorTitle.setText(titleRes);
        }
        if (errorMessage != null) {
            errorMessage.setText(messageRes);
        }
        setVisible(true);
    }

    public void showError(@NonNull String title, @NonNull String message) {
        if (errorTitle != null) {
            errorTitle.setText(title);
        }
        if (errorMessage != null) {
            errorMessage.setText(message);
        }
        setVisible(true);
    }

    public void showErrorWithTitle(@StringRes int titleRes) {
        if (errorTitle != null) {
            errorTitle.setText(titleRes);
        }
        if (errorMessage != null) {
            errorMessage.setVisibility(View.GONE);
        }
        setVisible(true);
    }

    public void showErrorWithMessage(@StringRes int messageRes) {
        if (errorTitle != null) {
            errorTitle.setVisibility(View.GONE);
        }
        if (errorMessage != null) {
            errorMessage.setText(messageRes);
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
