package io.antmedia.test.statistic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.scheduling.QuartzSchedulingService;

import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import io.antmedia.datastore.db.DataStoreFactory;
import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.AppSettings;
import io.antmedia.datastore.db.DataStore;
import io.antmedia.datastore.db.InMemoryDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.settings.ServerSettings;
import io.antmedia.statistic.HlsViewerStats;
import io.antmedia.statistic.IStreamStats;
import io.vertx.core.Vertx;


public class HlsViewerStatsTest {

	@Test
	public void testHLSViewerCount() {
		HlsViewerStats viewerStats = new HlsViewerStats();

		DataStore dataStore = new InMemoryDataStore("datastore");
		viewerStats.setDataStore(dataStore);
		
		String streamId = String.valueOf((Math.random() * 999999));
		
		Broadcast broadcast = new Broadcast();
		
		try {
			broadcast.setStreamId(streamId);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for (int i = 0; i < 100; i++) {
			String sessionId = String.valueOf((Math.random() * 999999));
			viewerStats.registerNewViewer(streamId, sessionId);
		}

		int viewerCount = viewerStats.getViewerCount(streamId);
		assertEquals(100, viewerCount);
		
		assertEquals(0, viewerStats.getViewerCount("no_streamid"));
		
		//Add same session ID
		for (int i = 0; i < 10; i++) {
			String sessionId = "sameSessionID";
			viewerStats.registerNewViewer(streamId, sessionId);
		}
		
		
		viewerCount = viewerStats.getViewerCount(streamId);
		assertEquals(101, viewerCount);

	}

	
	@Test
	public void testGetTimeout() {
		AppSettings settings = mock(AppSettings.class);
		when(settings.getHlsTime()).thenReturn("");
		
		int defaultValue = HlsViewerStats.DEFAULT_TIME_PERIOD_FOR_VIEWER_COUNT;
		assertEquals(defaultValue, HlsViewerStats.getTimeoutMSFromSettings(settings, defaultValue));
		
		when(settings.getHlsTime()).thenReturn("2");
		
		assertEquals(20000, HlsViewerStats.getTimeoutMSFromSettings(settings, defaultValue));
		
	}

	@Test
	public void testSetApplicationContext() {
		ApplicationContext context = mock(ApplicationContext.class);
		
		Vertx vertx = io.vertx.core.Vertx.vertx();		
		
		try {

			DataStoreFactory dsf = new DataStoreFactory();
			dsf.setDbType("memorydb");
			dsf.setDbName("datastore");
			when(context.getBean(DataStoreFactory.BEAN_NAME)).thenReturn(dsf);
			
			when(context.containsBean(AppSettings.BEAN_NAME)).thenReturn(true);
			
			AppSettings settings = mock(AppSettings.class);
			
			//set hls time to 1
			when(settings.getHlsTime()).thenReturn("1");
			
			when(context.getBean(AppSettings.BEAN_NAME)).thenReturn(settings);
			when(context.getBean(ServerSettings.BEAN_NAME)).thenReturn(new ServerSettings());
			
			HlsViewerStats viewerStats = new HlsViewerStats();
			
			viewerStats.vertx = vertx;
			
			viewerStats.setTimePeriodMS(1000);
			
			viewerStats.setApplicationContext(context);
			
			Broadcast broadcast = new Broadcast();
			broadcast.setStatus(AntMediaApplicationAdapter.BROADCAST_STATUS_BROADCASTING);
			broadcast.setName("name");
			
			dsf.setWriteStatsToDatastore(true);
			dsf.setApplicationContext(context);
			String streamId = dsf.getDataStore().save(broadcast);
			
			assertEquals(1000, viewerStats.getTimePeriodMS());
			assertEquals(10000, viewerStats.getTimeoutMS());
			
			
			
			
			// This sleep for the vertx timeout
			Thread.sleep(8000);
			
			String sessionId = "sessionId" + (int)(Math.random() * 10000);
			viewerStats.registerNewViewer(streamId, sessionId);
			viewerStats.registerNewViewer(streamId, sessionId);
			
			assertEquals(1, viewerStats.getViewerCount(streamId));
			assertEquals(1, viewerStats.getIncreaseCounterMap(streamId));
			assertEquals(1, viewerStats.getTotalViewerCount());
			
			
			// Check viwer is online
			Awaitility.await().atMost(5, TimeUnit.SECONDS).until(
					()-> dsf.getDataStore().get(streamId).getHlsViewerCount() == 1);
			
			// Wait some time for detect disconnect
			Awaitility.await().atMost(10, TimeUnit.SECONDS).until(
					()-> dsf.getDataStore().get(streamId).getHlsViewerCount() == 0);
			
			assertEquals(0, viewerStats.getViewerCount(streamId));
			assertEquals(0, viewerStats.getIncreaseCounterMap(streamId));
			assertEquals(0, viewerStats.getTotalViewerCount());
			
			// Broadcast finished test
			broadcast.setStatus(AntMediaApplicationAdapter.BROADCAST_STATUS_FINISHED);
			dsf.getDataStore().save(broadcast);
			
			viewerStats.registerNewViewer(streamId, sessionId);
			
			assertEquals(1, viewerStats.getViewerCount(streamId));
			assertEquals(1, viewerStats.getIncreaseCounterMap(streamId));
			assertEquals(1, viewerStats.getTotalViewerCount());
			
			Awaitility.await().atMost(10, TimeUnit.SECONDS).until(
					()-> dsf.getDataStore().get(streamId).getHlsViewerCount() == 0);
			
			
			Thread.sleep(5000);
			
			assertEquals(0, viewerStats.getViewerCount(streamId));
			assertEquals(0, viewerStats.getIncreaseCounterMap(streamId));
			assertEquals(0, viewerStats.getTotalViewerCount());
			
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		
		
	}

}
