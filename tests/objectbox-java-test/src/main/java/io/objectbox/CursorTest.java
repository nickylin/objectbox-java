package io.objectbox;

import io.objectbox.exception.DbException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;



import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CursorTest extends AbstractObjectBoxTest {

    @Override
    protected BoxStore createBoxStore() {
        return createBoxStore(true);
    }

    @Test
    public void testPutAndGetEntity() {
        TestEntity entity = new TestEntity();
        entity.setSimpleInt(1977);
        Transaction transaction = store.beginTx();
        Cursor<TestEntity> cursor = transaction.createCursor(TestEntity.class);
        long key = cursor.put(entity);

        TestEntity entityRead = cursor.get(key);
        assertNotNull(entityRead);
        assertEquals(1977, entityRead.getSimpleInt());

        cursor.close();
        transaction.abort();
    }

    @Test(expected = DbException.class)
    public void testPutEntityWithInvalidId() {
        TestEntity entity = new TestEntity();
        entity.setId(777);
        Transaction transaction = store.beginTx();
        Cursor<TestEntity> cursor = transaction.createCursor(TestEntity.class);
        try {
            cursor.put(entity);
        } finally {
            cursor.close();
            transaction.abort();
        }
    }

    @Test
    public void testGetNextEntity() {
        insertTestEntities("hello", "bye", "dummy");
        Transaction transaction = store.beginReadTx();
        Cursor<TestEntity> cursor = transaction.createCursor(TestEntity.class);
        TestEntity entity1 = cursor.get(1);
        assertEquals("hello", entity1.getSimpleString());
        TestEntity entity2 = cursor.next();
        assertEquals("bye", entity2.getSimpleString());
        TestEntity entity3 = cursor.next();
        assertEquals("dummy", entity3.getSimpleString());
        assertNull(cursor.next());
        cursor.close();
        transaction.abort();
    }

    // TODO split up into several test cases
    @Test
    public void testPutGetUpdateDeleteEntity() {

        // create an entity
        TestEntity entity = new TestEntity();
        entity.setSimpleInt(1977);
        entity.setSimpleLong(54321);
        String value1 = "lulu321";
        entity.setSimpleString(value1);
        Transaction transaction = store.beginTx();
        Cursor<TestEntity> cursor = transaction.createCursor(TestEntity.class);
        long key = cursor.put(entity);

        // get it
        TestEntity entityRead = cursor.get(key);
        assertNotNull(entityRead);
        assertEquals(1977, entityRead.getSimpleInt());
        assertEquals(54321, entityRead.getSimpleLong());
        assertEquals(value1, entityRead.getSimpleString());

        // and find via index
        assertEquals(key, cursor.lookupKeyUsingIndex(9, value1));
        assertEquals(key, cursor.find("simpleString", value1).getId());

        // change entity values
        String value2 = "lala123";
        entityRead.setSimpleString(value2);
        entityRead.setSimpleLong(12345);

        // and replace the old one with the new one
        cursor.put(entityRead);

        // indexes ok?
        assertNull(cursor.find("simpleString", value1));
        assertEquals(-1, cursor.lookupKeyUsingIndex(9, value1));

        assertEquals(key, cursor.find("simpleString", value2).getId());

        // get the changed entity
        entityRead = cursor.get(key);
        assertNotNull(entityRead);

        // and check if the values changed
        assertEquals(1977, entityRead.getSimpleInt());
        assertEquals(12345, entityRead.getSimpleLong());
        assertEquals(value2, entityRead.getSimpleString());

        // and remove it
        cursor.deleteEntity(key);

        // not in any index anymore
        assertNull(cursor.find("simpleString", value1));
        assertNull(cursor.find("simpleString", value2));

        cursor.close();
        transaction.abort();
    }

    @Test
    public void testPutSameIndexValue() {
        TestEntity entity = new TestEntity();
        String value = "lulu321";
        entity.setSimpleString(value);
        Transaction transaction = store.beginTx();

        Cursor<TestEntity> cursor = transaction.createCursor(TestEntity.class);
        long key = cursor.put(entity);
        // And again
        entity.setSimpleInt(1977);
        cursor.put(entity);
        assertEquals(key, cursor.lookupKeyUsingIndex(9, value));
        TestEntity read = cursor.get(key);
        assertEquals(1977, read.getSimpleInt());
        assertEquals(value, read.getSimpleString());
    }

    @Test
    public void testFindStringInEntity() {
        insertTestEntities("find me", "not me");

        Transaction transaction = store.beginTx();
        Cursor<TestEntity> cursor = transaction.createCursor(TestEntity.class);
        TestEntity entityRead = cursor.find("simpleString", "find me");
        assertNotNull(entityRead);
        assertEquals(1, entityRead.getId());

        cursor.close();
        transaction.abort();

        transaction = store.beginTx();
        cursor = transaction.createCursor(TestEntity.class);
        entityRead = cursor.find("simpleString", "not me");
        assertNotNull(entityRead);
        assertEquals(2, entityRead.getId());

        cursor.close();
        transaction.abort();

        transaction = store.beginTx();
        cursor = transaction.createCursor(TestEntity.class);
        assertNull(cursor.find("simpleString", "non-existing"));

        cursor.close();
        transaction.abort();
    }

    private void insertTestEntities(String... texts) {
        Transaction transaction = store.beginTx();
        Cursor<TestEntity> cursor = transaction.createCursor(TestEntity.class);
        for (String text : texts) {
            putEntity(cursor, 0, text);
        }
        cursor.close();
        transaction.commit();
    }

    private TestEntity putEntity(Cursor<TestEntity> cursor, long id, String text) {
        TestEntity entity = new TestEntity();
        entity.setSimpleString(text);
        entity.setId(id);
        cursor.put(entity);
        return entity;
    }

    @Test
    public void testFindStringInEntityWithIndex() {
        testFindStringInEntity();
    }

    @Test
    public void testLookupKeyUsingIndex() throws IOException {
        insertTestEntities("find me", "not me");

        Transaction transaction = store.beginTx();
        Cursor<TestEntity> cursor = transaction.createCursor(TestEntity.class);

        assertEquals(2, cursor.lookupKeyUsingIndex(9, "not me"));
        assertEquals(1, cursor.lookupKeyUsingIndex(9, "find me"));
        assertEquals(-1, cursor.lookupKeyUsingIndex(9, "peter pan"));

        cursor.close();
        transaction.abort();
    }


    @Test
    public void testLookupKeyUsingIndex_samePrefix() {
        insertTestEntities("aaa", "aa");

        Transaction transaction = store.beginTx();
        Cursor<TestEntity> cursor = transaction.createCursor(TestEntity.class);

        assertEquals(-1, cursor.lookupKeyUsingIndex(9, "a"));
        assertEquals(2, cursor.lookupKeyUsingIndex(9, "aa"));
        assertEquals(1, cursor.lookupKeyUsingIndex(9, "aaa"));
        assertEquals(-1, cursor.lookupKeyUsingIndex(9, "aaaa"));

        cursor.close();
        transaction.abort();
    }

    @Test
    public void testClose() {
        Transaction tx = store.beginReadTx();
        Cursor<TestEntity> cursor = tx.createCursor(TestEntity.class);
        assertFalse(cursor.isClosed());
        cursor.close();
        assertTrue(cursor.isClosed());

        // Double close should be fine
        cursor.close();
    }

    @Test
    public void testWriteTxBlocksOtherWriteTx() throws InterruptedException {
        long time = System.currentTimeMillis();
        Transaction tx = store.beginTx();
        long duration = System.currentTimeMillis() - time; // Usually 0 on desktop
        final CountDownLatch latchBeforeBeginTx = new CountDownLatch(1);
        final CountDownLatch latchAfterBeginTx = new CountDownLatch(1);
        new Thread() {
            @Override
            public void run() {
                latchBeforeBeginTx.countDown();
                Transaction tx2 = store.beginTx();
                latchAfterBeginTx.countDown();
                tx2.close();
            }
        }.start();
        assertTrue(latchBeforeBeginTx.await(1, TimeUnit.SECONDS));
        long waitTime = 50 + duration * 10;
        assertFalse(latchAfterBeginTx.await(waitTime, TimeUnit.MILLISECONDS));
        tx.close();
        assertTrue(latchAfterBeginTx.await(waitTime, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testGetPropertyId() {
        Transaction transaction = store.beginReadTx();
        Cursor<TestEntity> cursor = transaction.createCursor(TestEntity.class);
        assertEquals(1, cursor.getPropertyId("id"));
        assertEquals(2, cursor.getPropertyId("simpleBoolean"));
        assertEquals(3, cursor.getPropertyId("simpleByte"));
        assertEquals(4, cursor.getPropertyId("simpleShort"));
        transaction.abort();
    }
}
