package de.danoeh.antennapod.playback.service.internal;

import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@UnstableApi
public class NetworkRetryLoadErrorHandlingPolicy extends DefaultLoadErrorHandlingPolicy {
    static final long MAX_NETWORK_RETRY_DURATION_MS = TimeUnit.MINUTES.toMillis(5);

    private long retryLoadTaskId = C.INDEX_UNSET;
    private long retryBytesLoaded = 0;
    private long retryStartTimeMs = C.TIME_UNSET;

    @Override
    public synchronized long getRetryDelayMsFor(@NonNull LoadErrorInfo loadErrorInfo) {
        if (!isNetworkError(loadErrorInfo.exception)) {
            retryStartTimeMs = C.TIME_UNSET;
            return super.getRetryDelayMsFor(loadErrorInfo);
        }
        long now = getElapsedRealtime();
        long loadTaskId = loadErrorInfo.loadEventInfo.loadTaskId;
        long bytesLoaded = loadErrorInfo.loadEventInfo.bytesLoaded;
        if (retryStartTimeMs == C.TIME_UNSET || loadTaskId != retryLoadTaskId || bytesLoaded > retryBytesLoaded) {
            retryStartTimeMs = now;
        }
        retryLoadTaskId = loadTaskId;
        retryBytesLoaded = bytesLoaded;
        if (now - retryStartTimeMs > MAX_NETWORK_RETRY_DURATION_MS) {
            retryStartTimeMs = C.TIME_UNSET;
            return C.TIME_UNSET;
        }
        return super.getRetryDelayMsFor(loadErrorInfo);
    }

    @Override
    public synchronized int getMinimumLoadableRetryCount(int dataType) {
        if (retryStartTimeMs != C.TIME_UNSET) {
            return Integer.MAX_VALUE;
        }
        return super.getMinimumLoadableRetryCount(dataType);
    }

    @Override
    public synchronized void onLoadTaskConcluded(long loadTaskId) {
        if (loadTaskId == retryLoadTaskId) {
            retryStartTimeMs = C.TIME_UNSET;
        }
    }

    long getElapsedRealtime() {
        return SystemClock.elapsedRealtime();
    }

    static boolean isNetworkError(IOException exception) {
        if (!(exception instanceof HttpDataSource.HttpDataSourceException)
                || exception instanceof HttpDataSource.InvalidResponseCodeException) {
            return false;
        }
        int reason = ((HttpDataSource.HttpDataSourceException) exception).reason;
        return reason == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                || reason == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT;
    }
}
