/* ==================================================================
 * JdbcSettingsDaoTests.java - 7/06/2016 8:32:09 pm
 * 
 * Copyright 2007-2016 SolarNetwork.net Dev Team
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation; either version 2 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 * 02111-1307 USA
 * ==================================================================
 */

package net.solarnetwork.node.dao.jdbc.test;

import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import java.util.EnumSet;
import javax.annotation.Resource;
import javax.sql.DataSource;
import net.solarnetwork.node.Setting;
import net.solarnetwork.node.Setting.SettingFlag;
import net.solarnetwork.node.dao.SettingDao;
import net.solarnetwork.node.dao.jdbc.DatabaseSetup;
import net.solarnetwork.node.dao.jdbc.JdbcSettingDao;
import net.solarnetwork.node.test.AbstractNodeTransactionalTest;
import net.solarnetwork.util.StaticOptionalService;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test cases for the {@link JdbcSettingDao}.
 * 
 * @author matt
 * @version 1.0
 */
public class JdbcSettingsDaoTests extends AbstractNodeTransactionalTest {

	@Resource(name = "dataSource")
	private DataSource dataSource;

	@Resource(name = "txManager")
	private PlatformTransactionManager txManager;

	private JdbcSettingDao dao;
	private SettingDao settingDao; // to work with just public API
	private EventAdmin eventAdminMock;

	@Before
	public void setup() {
		DatabaseSetup setup = new DatabaseSetup();
		setup.setDataSource(dataSource);
		setup.init();

		ObjectMapper mapper = new ObjectMapper();
		mapper.setSerializationInclusion(Include.NON_NULL);

		dao = new JdbcSettingDao();
		dao.setDataSource(dataSource);
		dao.setTransactionTemplate(new TransactionTemplate(txManager));

		eventAdminMock = EasyMock.createMock(EventAdmin.class);
		dao.setEventAdmin(new StaticOptionalService<EventAdmin>(eventAdminMock));
		settingDao = dao;
	}

	private static final String TEST_KEY = "_test_key_";
	private static final String TEST_TYPE = "_test_type_";
	private static final String TEST_VALUE = "_test_value_";

	@Test
	public void insertVolatile() {
		replay(eventAdminMock);

		Setting s = new Setting();
		s.setKey(TEST_KEY);
		s.setType(TEST_TYPE);
		s.setValue(TEST_VALUE);
		s.setFlags(EnumSet.of(SettingFlag.Volatile));

		settingDao.storeSetting(s);

		verify(eventAdminMock);
	}

	@Test
	public void insertWithChangeEvent() {
		Capture<Event> eventCapture = new Capture<Event>();
		eventAdminMock.postEvent(EasyMock.capture(eventCapture));

		replay(eventAdminMock);

		settingDao.storeSetting(TEST_KEY, TEST_TYPE, TEST_VALUE);

		verify(eventAdminMock);

		Event event = eventCapture.getValue();
		Assert.assertNotNull(event);
		Assert.assertEquals(SettingDao.EVENT_TOPIC_SETTING_CHANGED, event.getTopic());
		Assert.assertEquals(TEST_KEY, event.getProperty(SettingDao.SETTING_KEY));
		Assert.assertEquals(TEST_TYPE, event.getProperty(SettingDao.SETTING_TYPE));
		Assert.assertEquals(TEST_VALUE, event.getProperty(SettingDao.SETTING_VALUE));
	}

	@Test
	public void deleteWithChangeEvent() {
		insertWithChangeEvent();
		EasyMock.reset(eventAdminMock);

		Capture<Event> eventCapture = new Capture<Event>();
		eventAdminMock.postEvent(EasyMock.capture(eventCapture));

		replay(eventAdminMock);

		boolean result = settingDao.deleteSetting(TEST_KEY, TEST_TYPE);

		verify(eventAdminMock);

		Assert.assertTrue(result);

		Event event = eventCapture.getValue();
		Assert.assertNotNull(event);
		Assert.assertEquals(SettingDao.EVENT_TOPIC_SETTING_CHANGED, event.getTopic());
		Assert.assertEquals(TEST_KEY, event.getProperty(SettingDao.SETTING_KEY));
		Assert.assertEquals(TEST_TYPE, event.getProperty(SettingDao.SETTING_TYPE));
		Assert.assertEquals(TEST_VALUE, event.getProperty(SettingDao.SETTING_VALUE));
	}

	@Test
	public void deleteVolatile() {
		insertVolatile();
		EasyMock.reset(eventAdminMock);

		replay(eventAdminMock);

		boolean result = settingDao.deleteSetting(TEST_KEY, TEST_TYPE);

		verify(eventAdminMock);

		Assert.assertTrue(result);
	}

}
