package de.danoeh.antennapod.playback.service.internal;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@RunWith(RobolectricTestRunner.class)
public class NetworkRetryLoadErrorHandlingPolicyTest {
    private static final long TASK_ID = 1;
    private static final long LIMIT = NetworkRetryLoadErrorHandlingPolicy.MAX_NETWORK_RETRY_DURATION_MS;

    private final DataSpec dataSpec = new DataSpec(Uri.parse("https://example.com/episode.mp3"));
    private long now;
    private NetworkRetryLoadErrorHandlingPolicy policy;

    @Before
    public void setUp() {
        now = 1000;
        policy = new NetworkRetryLoadErrorHandlingPolicy() {
            @Override
            long getElapsedRealtime() {
                return now;
            }
        };
    }

    @Test
    public void testNetworkErrorRetriesUntilLimit() {
        assertNotEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(error(networkError(), 1, 100)));
        assertEquals(Integer.MAX_VALUE, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA));

        now += LIMIT;
        assertNotEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(error(networkError(), 50, 100)));

        now += 1;
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(error(networkError(), 51, 100)));
        assertEquals(3, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA));
    }

    @Test
    public void testProgressResetsLimit() {
        policy.getRetryDelayMsFor(error(networkError(), 1, 100));
        now += LIMIT;
        policy.getRetryDelayMsFor(error(networkError(), 2, 200));
        now += LIMIT;
        assertNotEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(error(networkError(), 3, 200)));
        now += 1;
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(error(networkError(), 4, 200)));
    }

    @Test
    public void testNonNetworkErrorIsNotExtended() {
        IOException notFound = new HttpDataSource.InvalidResponseCodeException(404, "Not Found",
                null, Collections.emptyMap(), dataSpec, new byte[0]);
        LoadErrorInfo info = error(notFound, 1, 0);
        assertEquals(new DefaultLoadErrorHandlingPolicy().getRetryDelayMsFor(info), policy.getRetryDelayMsFor(info));
        assertEquals(3, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA));
    }

    @Test
    public void testLoadTaskConcludedResetsRetry() {
        policy.getRetryDelayMsFor(error(networkError(), 1, 100));
        policy.onLoadTaskConcluded(TASK_ID);
        assertEquals(3, policy.getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA));
    }

    private IOException networkError() {
        return new HttpDataSource.HttpDataSourceException(new UnknownHostException(), dataSpec,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN);
    }

    private LoadErrorInfo error(IOException exception, int errorCount, long bytesLoaded) {
        LoadEventInfo eventInfo = new LoadEventInfo(TASK_ID, dataSpec, dataSpec.uri,
                Collections.emptyMap(), now, 0, bytesLoaded);
        return new LoadErrorInfo(eventInfo, new MediaLoadData(C.DATA_TYPE_MEDIA), exception, errorCount);
    }
}
