/*
 * Copyright (c) 2011 by the original author(s).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.mongodb.core.mapping;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.springframework.data.mongodb.core.query.Criteria.*;
import static org.springframework.data.mongodb.core.query.Query.*;
import static org.springframework.data.mongodb.core.query.Update.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.MongoCollectionUtils;
import org.springframework.data.mongodb.core.CollectionCallback;
import org.springframework.data.mongodb.core.MongoDbUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Order;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Jon Brisbin <jbrisbin@vmware.com>
 */
public class MappingTests {

	private static final Log LOGGER = LogFactory.getLog(MongoDbUtils.class);
	private final String[] collectionsToDrop = new String[]{
			MongoCollectionUtils.getPreferredCollectionName(Person.class),
			MongoCollectionUtils.getPreferredCollectionName(PersonMapProperty.class),
			MongoCollectionUtils.getPreferredCollectionName(PersonWithObjectId.class),
			MongoCollectionUtils.getPreferredCollectionName(PersonPojoIntId.class),
			MongoCollectionUtils.getPreferredCollectionName(PersonPojoLongId.class),
			MongoCollectionUtils.getPreferredCollectionName(PersonPojoStringId.class),
			MongoCollectionUtils.getPreferredCollectionName(PersonCustomIdName.class),
			MongoCollectionUtils.getPreferredCollectionName(PersonMultiDimArrays.class),
			MongoCollectionUtils.getPreferredCollectionName(PersonMultiCollection.class),
			MongoCollectionUtils.getPreferredCollectionName(PersonWithDbRef.class),
			MongoCollectionUtils.getPreferredCollectionName(PersonNullProperties.class),
			MongoCollectionUtils.getPreferredCollectionName(Account.class),
			MongoCollectionUtils.getPreferredCollectionName(PrimitiveId.class),
			"foobar", "geolocation", "person1", "person2", "account"};

	ApplicationContext applicationContext;
	Mongo mongo;
	MongoTemplate template;
	MongoMappingContext mappingContext;

	@Before
	public void setUp() throws Exception {
		mongo = new Mongo();
		DB db = mongo.getDB("database");
		for (String coll : collectionsToDrop) {
			db.getCollection(coll).drop();
		}
		applicationContext = new ClassPathXmlApplicationContext("/mapping.xml");
		template = applicationContext.getBean(MongoTemplate.class);
		mappingContext = (MongoMappingContext) ReflectionTestUtils.getField(template, "mappingContext");
	}

	@Test
	public void testGeneratedId() {
		GeneratedId genId = new GeneratedId("test");
		template.insert(genId);

		assertNotNull(genId.getId());
	}

	@Test
	public void testPersonPojo() throws Exception {

		LOGGER.info("about to create new personpojo");
		PersonWithObjectId p = new PersonWithObjectId(12345, "Person", "Pojo");
		LOGGER.info("about to insert");
		template.insert(p);
		LOGGER.info("done inserting");
		assertNotNull(p.getId());

		List<PersonWithObjectId> result = template.find(new Query(Criteria.where("ssn").is(12345)), PersonWithObjectId.class);
		assertThat(result.size(), is(1));
		assertThat(result.get(0).getSsn(), is(12345));
	}

	@Test
	public void testPersonWithCustomIdName() {

		PersonCustomIdName p = new PersonCustomIdName(123456, "Custom Id", null);
		template.insert(p);

		List<PersonCustomIdName> result = template.find(new Query(Criteria.where("lastName").is(p.getLastName())),
				PersonCustomIdName.class);
		assertThat(result.size(), is(1));
		assertThat(result.get(0).getFirstName(), is("Custom Id"));

		PersonCustomIdName p2 = new PersonCustomIdName(654321, "Custom Id", "LastName");
		template.insert(p2);

		List<PersonCustomIdName> result2 = template.find(new Query(Criteria.where("lastName").is("LastName")),
				PersonCustomIdName.class);
		assertThat(result2.size(), is(1));
		assertNotNull(result2.get(0).getLastName());
		assertThat(result2.get(0).getLastName(), is("LastName"));

		// Test "in" query
		List<PersonCustomIdName> result3 = template.find(new Query(Criteria.where("lastName").in("LastName")),
				PersonCustomIdName.class);
		assertThat(result3.size(), is(1));
		assertNotNull(result3.get(0).getLastName());
		assertThat(result3.get(0).getLastName(), is("LastName"));
	}

	@Test
	public void testPersonMapProperty() {
		PersonMapProperty p = new PersonMapProperty(1234567, "Map", "Property");
		Map<String, AccountPojo> accounts = new HashMap<String, AccountPojo>();

		AccountPojo checking = new AccountPojo("checking", 1000.0f);
		AccountPojo savings = new AccountPojo("savings", 10000.0f);

		accounts.put("checking", checking);
		accounts.put("savings", savings);
		p.setAccounts(accounts);

		template.insert(p);
		assertNotNull(p.getId());

		List<PersonMapProperty> result = template.find(new Query(Criteria.where("ssn").is(1234567)),
				PersonMapProperty.class);
		assertThat(result.size(), is(1));
		assertThat(result.get(0).getAccounts().size(), is(2));
		assertThat(result.get(0).getAccounts().get("checking").getBalance(), is(1000.0f));
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void testWriteEntity() {

		Address addr = new Address();
		addr.setLines(new String[]{"1234 W. 1st Street", "Apt. 12"});
		addr.setCity("Anytown");
		addr.setPostalCode(12345);
		addr.setCountry("USA");

		Account acct = new Account();
		acct.setBalance(1000.00f);
		template.insert(acct, "account");

		List<Account> accounts = new ArrayList<Account>();
		accounts.add(acct);

		Person p = new Person(123456789, "John", "Doe", 37, addr);
		p.setAccounts(accounts);
		template.insert(p, "person");

		Account newAcct = new Account();
		newAcct.setBalance(10000.00f);
		template.insert(newAcct, "account");

		accounts.add(newAcct);
		template.save(p, "person");

		assertNotNull(p.getId());

		List<Person> result = template.find(new Query(Criteria.where("ssn").is(123456789)), Person.class);
		assertThat(result.size(), is(1));
		assertThat(result.get(0).getAddress().getCountry(), is("USA"));
		assertThat(result.get(0).getAccounts(), notNullValue());
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void testUniqueIndex() {
		Address addr = new Address();
		addr.setLines(new String[]{"1234 W. 1st Street", "Apt. 12"});
		addr.setCity("Anytown");
		addr.setPostalCode(12345);
		addr.setCountry("USA");

		Person p1 = new Person(1234567890, "John", "Doe", 37, addr);
		Person p2 = new Person(1234567890, "Jane", "Doe", 38, addr);

		List<Person> persons = new ArrayList<Person>();
		persons.add(p1);
		persons.add(p2);
		template.insert(persons, MongoCollectionUtils.getPreferredCollectionName(Person.class));

		List<Person> result = template.find(new Query(Criteria.where("ssn").is(1234567890)), Person.class);
		assertThat(result.size(), is(1));
	}

	@Test
	public void testCustomCollectionInList() {
		List<BasePerson> persons = new ArrayList<BasePerson>();
		persons.add(new PersonCustomCollection1(55555, "Person", "One"));
		persons.add(new PersonCustomCollection2(66666, "Person", "Two"));
		template.insertAll(persons);

		List<PersonCustomCollection1> p1Results = template.find(new Query(Criteria.where("ssn").is(55555)), PersonCustomCollection1.class,
				"person1");
		List<PersonCustomCollection2> p2Results = template.find(new Query(Criteria.where("ssn").is(66666)), PersonCustomCollection2.class,
				"person2");
		assertThat(p1Results.size(), is(1));
		assertThat(p2Results.size(), is(1));
	}

	@Test
	public void testPrimitivesAndCustomCollectionName() {
		Location loc = new Location(new double[]{1.0, 2.0}, new int[]{1, 2, 3, 4}, new float[]{1.0f, 2.0f});
		template.insert(loc);

		List<Location> result = template.find(new Query(Criteria.where("_id").is(loc.getId())), Location.class, "places");
		assertThat(result.size(), is(1));
	}

	@Test
	public void testIndexesCreatedInRightCollection() {
		CustomCollectionWithIndex ccwi = new CustomCollectionWithIndex("test");
		template.insert(ccwi);

		assertTrue(template.execute("foobar", new CollectionCallback<Boolean>() {
			public Boolean doInCollection(DBCollection collection) throws MongoException, DataAccessException {
				List<DBObject> indexes = collection.getIndexInfo();
				for (DBObject dbo : indexes) {
                    if (dbo.get("name") != null && dbo.get("name") instanceof String && ((String)dbo.get("name")).startsWith("name")) {
						return true;
					}
				}
				return false;
			}
		}));

		DetectedCollectionWithIndex dcwi = new DetectedCollectionWithIndex("test");
		template.insert(dcwi);

		assertTrue(template.execute(MongoCollectionUtils.getPreferredCollectionName(DetectedCollectionWithIndex.class),
				new CollectionCallback<Boolean>() {
					public Boolean doInCollection(DBCollection collection) throws MongoException, DataAccessException {
						List<DBObject> indexes = collection.getIndexInfo();
						for (DBObject dbo : indexes) {
							if (dbo.get("name") != null && dbo.get("name") instanceof String && ((String)dbo.get("name")).startsWith("name")) {
								return true;
							}
						}
						return false;
					}
				}));
	}

	@Test
	public void testMultiDimensionalArrayProperties() {
		String[][] grid = new String[][]{new String[]{"1", "2", "3", "4"}, new String[]{"5", "6", "7", "8"},
				new String[]{"9", "10", "11", "12"}};
		PersonMultiDimArrays p = new PersonMultiDimArrays(123, "Multi", "Dimensional", grid);

		template.insert(p);
		List<PersonMultiDimArrays> result = template.find(new Query(Criteria.where("ssn").is(123)),
				PersonMultiDimArrays.class);
		assertThat(result.size(), is(1));

		assertThat(result.get(0).getGrid().length, is(3));
	}

	@Test
	public void testMultiDimensionalCollectionProperties() {
		List<List<String>> grid = new ArrayList<List<String>>();
		ArrayList<String> inner = new ArrayList<String>();
		inner.add("1");
		inner.add("2");
		inner.add("3");
		inner.add("4");
		grid.add(inner);

		PersonMultiCollection p = new PersonMultiCollection(321, "Multi Dim", "Collections", grid);
		template.insert(p);

		List<PersonMultiCollection> result = template.find(new Query(Criteria.where("ssn").is(321)),
				PersonMultiCollection.class);
		assertThat(result.size(), is(1));

		assertThat(result.get(0).getGrid().size(), is(1));
	}

	@Test
	public void testDbRef() {
		double[] pos = new double[]{37.0625, -95.677068};
		GeoLocation geo = new GeoLocation(pos);
		template.insert(geo);

		PersonWithDbRef p = new PersonWithDbRef(4321, "With", "DBRef", geo);
		template.insert(p);

		List<PersonWithDbRef> result = template.find(new Query(Criteria.where("ssn").is(4321)), PersonWithDbRef.class);
		assertThat(result.size(), is(1));
		assertThat(result.get(0).getHome().getLocation(), is(pos));
	}

	@Test
	public void testPersonWithNullProperties() {
		PersonNullProperties p = new PersonNullProperties();
		template.insert(p);

		assertNotNull(p.getId());
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void testQueryUpdate() {
		Address addr = new Address();
		addr.setLines(new String[]{"1234 W. 1st Street", "Apt. 12"});
		addr.setCity("Anytown");
		addr.setPostalCode(12345);
		addr.setCountry("USA");

		Person p = new Person(1111, "Query", "Update", 37, addr);
		template.insert(p);

		addr.setCity("New Town");
		template.updateFirst(query(where("ssn").is(1111)), update("address", addr), Person.class);

		Person p2 = template.findOne(query(where("ssn").is(1111)), Person.class);
		assertThat(p2.getAddress().getCity(), is("New Town"));
	}

	@Test
	public void testOrQuery() {
		PersonWithObjectId p1 = new PersonWithObjectId(1, "first", "");
		template.save(p1);
		PersonWithObjectId p2 = new PersonWithObjectId(2, "second", "");
		template.save(p2);

		Query one = query(where("ssn").is(1));
		Query two = query(where("ssn").is(2));
		List<PersonWithObjectId> results = template.find(new Query().or(one, two), PersonWithObjectId.class);

		assertNotNull(results);
		assertThat(results.size(), is(2));
		assertThat(results.get(1).getSsn(), is(2));
	}

	@Test
	public void testPrimitivesAsIds() {
		PrimitiveId p = new PrimitiveId(1);
		p.setText("test text");

		template.save(p);

		PrimitiveId p2 = template.findOne(query(where("id").is(1)), PrimitiveId.class);
		assertNotNull(p2);
	}

	@Test
	public void testNoMappingAnnotationsUsingIntAsId() {
		PersonPojoIntId p = new PersonPojoIntId(1, "Text");
		template.insert(p);
		template.updateFirst(query(where("id").is(1)), update("text", "New Text"), PersonPojoIntId.class);

		PersonPojoIntId p2 = template.findOne(query(where("id").is(1)), PersonPojoIntId.class);
		assertEquals("New Text", p2.getText());

		p.setText("Different Text");
		template.save(p);

		PersonPojoIntId p3 = template.findOne(query(where("id").is(1)), PersonPojoIntId.class);
		assertEquals("Different Text", p3.getText());

	}

	@Test
	public void testNoMappingAnnotationsUsingLongAsId() {
		PersonPojoLongId p = new PersonPojoLongId(1, "Text");
		template.insert(p);
		template.updateFirst(query(where("id").is(1)), update("text", "New Text"),
				PersonPojoLongId.class);

		PersonPojoLongId p2 = template.findOne(query(where("id").is(1)),
				PersonPojoLongId.class);
		assertEquals("New Text", p2.getText());

		p.setText("Different Text");
		template.save(p);

		PersonPojoLongId p3 = template.findOne(query(where("id").is(1)),
				PersonPojoLongId.class);
		assertEquals("Different Text", p3.getText());

	}

	@Test
	public void testNoMappingAnnotationsUsingStringAsId() {
		//Assign the String Id in code
		PersonPojoStringId p = new PersonPojoStringId("1", "Text");
		template.insert(p);
		template.updateFirst(query(where("id").is("1")), update("text", "New Text"),
				PersonPojoStringId.class);

		PersonPojoStringId p2 = template.findOne(query(where("id").is("1")),
				PersonPojoStringId.class);
		assertEquals("New Text", p2.getText());

		p.setText("Different Text");
		template.save(p);

		PersonPojoStringId p3 = template.findOne(query(where("id").is("1")),
				PersonPojoStringId.class);
		assertEquals("Different Text", p3.getText());


		PersonPojoStringId p4 = new PersonPojoStringId("2", "Text-2");
		template.insert(p4);

		Query q = query(where("id").in("1", "2"));
		q.sort().on("id", Order.ASCENDING);
		List<PersonPojoStringId> people = template.find(q, PersonPojoStringId.class);
		assertEquals(2, people.size());

	}

	@Test
	public void testPersonWithLongDBRef() {
		PersonPojoLongId personPojoLongId = new PersonPojoLongId(12L, "PersonWithLongDBRef");
		template.insert(personPojoLongId);

		PersonWithLongDBRef personWithLongDBRef = new PersonWithLongDBRef(21, "PersonWith", "LongDBRef", personPojoLongId);
		template.insert(personWithLongDBRef);

		Query q = query(where("ssn").is(21));
		PersonWithLongDBRef p2 = template.findOne(q, PersonWithLongDBRef.class);
		assertNotNull(p2);
		assertNotNull(p2.getPersonPojoLongId());
		assertEquals(12L, p2.getPersonPojoLongId().getId());
	}
	
	/**
	 * @see DATADOC-275
	 */
	@Test
	public void readsAndWritesDBRefsCorrectly() {

		template.dropCollection(Item.class);
		template.dropCollection(Container.class);
		
		Item item = new Item();
		Item items = new Item();
		template.insert(item);
		template.insert(items);
		
		Container container = new Container();
		container.item = item;
		container.items = Arrays.asList(items);
		
		template.insert(container);
		
		Container result = template.findOne(query(where("id").is(container.id)), Container.class);
		assertThat(result.item.id, is(item.id));
		assertThat(result.items.size(), is(1));
		assertThat(result.items.get(0).id, is(items.id));
	}
	
	
	class Container {
		
		@Id
		final String id;
		
		public Container() {
			id = new ObjectId().toString();
		}
		
		@DBRef
		Item item;
		@DBRef
		List<Item> items;
	}
	
	class Item {
		
		@Id
		final String id;
		
		public Item() {
			this.id = new ObjectId().toString();
		}
	}
}
