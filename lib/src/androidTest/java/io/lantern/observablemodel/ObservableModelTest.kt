/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package io.lantern.observablemodel

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import javax.crypto.KeyGenerator

@RunWith(AndroidJUnit4::class)
class ObservableModelTest {
    private var tempDir: Path? = null

    @Test
    fun testQuery() {
        val model = buildModel()
        model.mutate { tx ->
            tx.putAll(
                mapOf(
                    "/contacts/32af234asdf324" to "That Person",
                    "/contacts/32af234asdf324/messages_by_timestamp/1" to "/messages/c",
                    "/contacts/32af234asdf324/messages_by_timestamp/2" to "/messages/a",
                    "/contacts/32af234asdf324/messages_by_timestamp/3" to "/messages/b",
                    "/contacts/32af234asdf324/messages_by_timestamp/4" to "/messages/e", // this one doesn't exist
                    "/messages/c" to "Message C",
                    "/messages/d" to "Message D", // this one isn't referenced by messages_by_timestamp
                    "/messages/a" to "Message A",
                    "/messages/b" to "Message B",
                )
            )
        }
        assertEquals("That Person", model.get("/contacts/32af234asdf324"))
        assertEquals(
            Raw(model.serde, "That Person"),
            model.getRaw<String>("/contacts/32af234asdf324")
        )

        assertEquals(
            arrayListOf(
                Entry("/messages/a", "Message A"),
                Entry("/messages/b", "Message B"),
                Entry("/messages/c", "Message C"),
                Entry("/messages/d", "Message D")
            ), model.list<String>("/messages/%")
        )
        assertEquals(
            arrayListOf(
                Entry("/messages/a", Raw(model.serde, "Message A")),
                Entry("/messages/b", Raw(model.serde, "Message B")),
                Entry("/messages/c", Raw(model.serde, "Message C")),
                Entry("/messages/d", Raw(model.serde, "Message D"))
            ), model.listRaw<String>("/messages/%")
        )

        assertEquals(
            arrayListOf(
                Detail(
                    "/contacts/32af234asdf324/messages_by_timestamp/3",
                    "/messages/b",
                    "Message B"
                ),
                Detail(
                    "/contacts/32af234asdf324/messages_by_timestamp/2",
                    "/messages/a",
                    "Message A"
                ),
                Detail(
                    "/contacts/32af234asdf324/messages_by_timestamp/1",
                    "/messages/c",
                    "Message C"
                ),
            ), model.listDetails<String>(
                "/contacts/32af234asdf324/messages_by_timestamp/%",
                0,
                10,
                reverseSort = true
            )
        )
        assertEquals(
            arrayListOf(
                Detail(
                    "/contacts/32af234asdf324/messages_by_timestamp/3",
                    "/messages/b",
                    Raw(model.serde, "Message B")
                ),
                Detail(
                    "/contacts/32af234asdf324/messages_by_timestamp/2",
                    "/messages/a",
                    Raw(model.serde, "Message A")
                ),
                Detail(
                    "/contacts/32af234asdf324/messages_by_timestamp/1",
                    "/messages/c",
                    Raw(model.serde, "Message C")
                ),
            ), model.listDetailsRaw<String>(
                "/contacts/32af234asdf324/messages_by_timestamp/%",
                0,
                10,
                reverseSort = true
            )
        )

        assertEquals(
            arrayListOf("Message C", "Message A", "Message B"),
            model.listDetails<String>("/contacts/32af234asdf324/messages_by_timestamp/%", 0, 10)
                .map { it.value }
        )
        assertEquals(
            arrayListOf("Message A"),
            model.listDetails<String>("/contacts/32af234asdf324/messages_by_timestamp/%", 1, 1)
                .map { it.value }
        )
        assertEquals(
            0,
            model.listDetails<String>(
                "/contacts/32af234asdf324/messages_by_timestamp/%",
                3,
                10
            ).size
        )
        model.close()
    }

    @Test
    fun testFullTextQuery() {
        val vals = mapOf(
            "/messages/c" to Message("Message C blah blah"),
            "/messages/d" to Message("Message D blah blah blah"),
            "/messages/a" to Message("Message A blah"),
            "/messages/b" to Message("Message B"),
        )
        val model = buildModel()
        model.mutate { tx ->
            vals.forEach { (path, value) ->
                tx.put(path, value, fullText = value.body)
            }
            tx.putAll(
                mapOf(
                    "/list/1" to "/messages/a",
                    "/list/2" to "/messages/b",
                    "/list/3" to "/messages/c",
                    "/list/4" to "/messages/d",
                )
            )
        }

        assertEquals(
            arrayListOf(
                Entry("/messages/a", Message("Message A blah")),
                Entry("/messages/b", Message("Message B")),
                Entry("/messages/c", Message("Message C blah blah")),
                Entry("/messages/d", Message("Message D blah blah blah"))
            ), model.list<String>("/messages/%")
        )

        assertEquals(
            arrayListOf(
                Entry("/messages/d", Message("Message D blah blah blah")),
                Entry("/messages/c", Message("Message C blah blah")),
                Entry("/messages/a", Message("Message A blah"))
            ), model.list<String>("/%", fullTextSearch = "blah")
        )

        assertEquals(
            arrayListOf(
                Detail("/list/4", "/messages/d", Message("Message D blah blah blah")),
                Detail("/list/3", "/messages/c", Message("Message C blah blah")),
                Detail("/list/1", "/messages/a", Message("Message A blah"))
            ), model.listDetails<String>("/list/%", fullTextSearch = "blah")
        )

        // now delete
        model.mutate { tx ->
            // delete an entry including the full text index
            tx.delete("/messages/d", extractFullText = { msg: Message -> msg.body })
            // add the entry back without full-text indexing to make sure it doesn't show up in results
            tx.put("/messages/d", Message("Message D blah blah blah"))
            // delete another entry without deleting the full text index
            tx.delete("/messages/c")
        }

        assertEquals(
            arrayListOf(
                Entry("/messages/a", Message("Message A blah"))
            ), model.list<String>("/%", fullTextSearch = "blah")
        )

        model.close()
    }

    @Test
    fun testSubscribeDirectLate() {
        val model = buildModel()
        var theValue = "thevalue";

        model.mutate { tx ->
            tx.put("path", theValue)
        }
        model.subscribe(object : Subscriber<String>("100", "path") {
            override fun onUpdate(path: String, value: String) {
                assertEquals("path", path)
                assertEquals(theValue, value)
            }

            override fun onDelete(path: String) {
            }
        })

        theValue = "new value"
        model.mutate { tx ->
            tx.put("path", theValue)
        }
        model.close()
    }

    @Test
    fun testSubscribeDirect() {
        val model = buildModel()
        var currentValue = "original value";

        model.subscribe(object : Subscriber<String>("100", "path") {
            override fun onUpdate(path: String, value: String) {
                fail("this subscriber was replaced and should never have been notified")
            }

            override fun onDelete(path: String) {
            }
        })

        try {
            model.subscribe(object : Subscriber<String>("100", "path") {
                override fun onUpdate(path: String, value: String) {
                }

                override fun onDelete(path: String) {
                }
            })
            fail("re-registering already registered subscriber ID should not be allowed")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        model.unsubscribe("100")

        model.subscribe(object : Subscriber<String>("100", "path") {
            override fun onUpdate(path: String, value: String) {
                assertEquals("path", path)
                // this subscriber should only ever get the original value because we unsubscribe later
                assertEquals("original value", value)
            }

            override fun onDelete(path: String) {
            }
        })

        var calledDelete = false

        model.subscribe(object : Subscriber<String>("101", "path") {
            override fun onUpdate(path: String, value: String) {
                assertEquals("path", path)
                // this subscriber should always get the current value becasue we don't unsubscribe it
                assertEquals(currentValue, value)
            }

            override fun onDelete(path: String) {
                assertEquals("path", path)
                calledDelete = true
            }
        })

        model.unsubscribe("100")

        model.mutate { tx ->
            tx.put("path", currentValue)
        }
        currentValue = "new value"
        model.mutate { tx ->
            tx.put("path", currentValue)
        }

        model.mutate { tx -> tx.delete("path") }
        assertTrue(calledDelete)

        model.close()
    }

    @Test
    fun testSubscribePrefixNoInit() {
        val model = buildModel()
        model.mutate { tx ->
            tx.putAll(
                mapOf(
                    "/path/1" to "1",
                    "/path/2" to "2",
                )
            )
        }

        var updateCalled = false
        var deleteCalled = false
        model.subscribe(object : Subscriber<String>("100", "/path") {
            override fun onUpdate(path: String, value: String) {
                assertEquals("/path/3", path)
                assertEquals("3", value)
                updateCalled = true
            }

            override fun onDelete(path: String) {
                assertEquals("/path/1", path)
                deleteCalled = true
            }
        }, receiveInitial = false)
        model.subscribe(object : Subscriber<String>("101", "/pa/") {
            override fun onUpdate(path: String, value: String) {
                fail("subscriber with no common prefix shouldn't get updates")
            }

            override fun onDelete(path: String) {
                fail("subscriber with no common prefix shouldn't get deletions")
            }
        }, receiveInitial = false)

        model.mutate { tx ->
            tx.delete("/path/1")
            tx.put("/path/3", "3")
        }

        assertTrue(updateCalled)
        assertTrue(deleteCalled)

        model.close()
    }

    @Test
    fun testSubscribePrefixInit() {
        val model = buildModel()
        model.mutate { tx ->
            tx.putAll(
                mapOf(
                    "/path/1" to "1",
                    "/path/2" to "2",
                    "/pa/1" to "1",
                )
            )
        }

        var updates = 0
        model.subscribe(object : Subscriber<String>("100", "/path") {
            override fun onUpdate(path: String, value: String) {
                assertTrue(path.startsWith("/path/"))
                assertEquals(path.substring(path.length - 1), value)
                updates += 1
            }

            override fun onDelete(path: String) {
            }
        })

        assertEquals(2, updates)
        model.close()
    }

    @Test
    fun testSubscribeDetailsPrefixInit() {
        val model = buildModel()
        model.mutate { tx ->
            tx.putAll(
                mapOf(
                    "/detail/1" to "1",
                    "/detail/2" to "2",
                    "/list/1" to "/detail/2",
                    "/list/2" to "/detail/1"
                )
            )
        }

        val updates = HashSet<Entry<String>>()
        val deletions = HashSet<String>()

        model.subscribeDetails(object : Subscriber<String>("100", "/list/") {
            override fun onUpdate(path: String, value: String) {
                updates.add(Entry(path, value))
            }

            override fun onDelete(path: String) {
                deletions.add(path)
            }
        })

        model.mutate { tx ->
            tx.put("/detail/1", "11")
            tx.put("/list/3", "/detail/3")
            tx.put("/detail/3", "3")
            tx.put(
                "/list/4",
                "/detail/unknown"
            ) // since this doesn't have details, we shouldn't get notified about it
            tx.put(
                "/detail/4",
                "4"
            ) // since this detail doesn't link back to a path in the list, we shouldn't get notified about it
        }

        model.mutate { tx ->
            tx.delete("/detail/2") // should show up as a deletion of /list/2
            tx.delete("/list/2")
        }

        assertEquals(
            setOf(
                Entry("/list/1", "2"),
                Entry("/list/2", "11"),
                Entry("/list/2", "1"),
                Entry("/list/3", "3"),
            ), updates
        )

        assertEquals(setOf("/list/1", "/list/2"), deletions)
        model.close()
    }

    @Test
    fun testDurability() {
        val model = buildModel()
        model.mutate { tx ->
            tx.putAll(mapOf("path1" to "value1", "path2" to "value2"))
        }

        try {
            model.mutate { tx ->
                tx.put("path3", "value3")
                throw Exception("I failed")
            }
        } catch (_: Throwable) {
            // ignore exception
        }
        model.close()

        // open the model again
        val model2 = buildModel()
        assertEquals("value1", model2.get<String>("path1"))
        assertEquals("value2", model2.get<String>("path2"))
        assertNull("path3 should have been rolled back", model2.get("path3"))
        model2.close()
    }

    @Test
    fun testSharedPreferences() {
        val fallback =
            InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(
                "testPreferences",
                Context.MODE_PRIVATE
            )
        fallback.edit().putBoolean("fboolean", true).putFloat("ffloat", 1.1.toFloat())
            .putInt("fint", 2).putLong("flong", 3).putString("fstring", "fallbackstring")
            .putBoolean("boolean", true).putFloat("float", 1.1.toFloat()).putInt("int", 2)
            .putLong("long", 3).putString("string", "fallbackstring").commit()
        val model = buildModel();
        val prefs = model.asSharedPreferences("/prefs/", fallback)
        prefs.edit().putBoolean("boolean", true).putFloat("float", 11.11.toFloat())
            .putInt("int", 22)
            .putLong("long", 33).putString("string", "realstring").commit()

        assertEquals(
            mapOf(
                "fboolean" to true,
                "ffloat" to 1.1.toFloat(),
                "fint" to 2,
                "flong" to 3.toLong(),
                "fstring" to "fallbackstring",
                "boolean" to true,
                "float" to 11.11.toFloat(),
                "int" to 22,
                "long" to 33.toLong(),
                "string" to "realstring"
            ), prefs.all
        )

        assertTrue(model.get<Boolean>("/prefs/boolean") ?: false)
        assertTrue(prefs.getBoolean("boolean", false))
        assertTrue(prefs.getBoolean("fboolean", false))
        assertTrue(prefs.getBoolean("uboolean", true))

        assertEquals(11.11.toFloat(), model.get<Float>("/prefs/float"))
        assertEquals(11.11.toFloat(), prefs.getFloat("float", 111.111.toFloat()))
        assertEquals(1.1.toFloat(), prefs.getFloat("ffloat", 111.111.toFloat()))
        assertEquals(111.111.toFloat(), prefs.getFloat("ufloat", 111.111.toFloat()))

        assertEquals(22, model.get<Int>("/prefs/int"))
        assertEquals(22, prefs.getInt("int", 222))
        assertEquals(2, prefs.getInt("fint", 222))
        assertEquals(222, prefs.getInt("uint", 222))

        assertEquals(33.toLong(), model.get<Long>("/prefs/long"))
        assertEquals(33.toLong(), prefs.getLong("long", 333))
        assertEquals(3.toLong(), prefs.getLong("flong", 333))
        assertEquals(333.toLong(), prefs.getLong("ulong", 333))

        assertEquals("realstring", model.get<String>("/prefs/string"))
        assertEquals("realstring", prefs.getString("string", "unknownstring"))
        assertEquals("fallbackstring", prefs.getString("fstring", "unknownstring"))
        assertEquals("unknownstring", prefs.getString("ustring", "unknownstring"))

        prefs.edit().remove("boolean").remove("float").remove("int").remove("long").remove("string")
            .apply()
        assertNull(model.get<Boolean>("/prefs/boolean"))
        assertTrue(prefs.getBoolean("boolean", false))

        assertNull(model.get<Float>("/prefs/float"))
        assertEquals(1.1.toFloat(), prefs.getFloat("float", 111.111.toFloat()))

        assertNull(model.get<Int>("/prefs/int"))
        assertEquals(2, prefs.getInt("int", 222))

        assertNull(model.get<Long>("/prefs/long"))
        assertEquals(3.toLong(), prefs.getLong("long", 333))

        assertNull(model.get<String>("/prefs/string"))
        assertEquals("fallbackstring", prefs.getString("string", "unknownstring"))
    }

    @Test
    fun testPreferencesListener() {
        val model = buildModel()
        val prefs = model.asSharedPreferences("/prefs/")

        val updatedKeys = HashSet<String>()
        val listener = object : SharedPreferences.OnSharedPreferenceChangeListener {
            override fun onSharedPreferenceChanged(
                sharedPreferences: SharedPreferences?,
                key: String?
            ) {
                updatedKeys.add(key!!)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        prefs.edit().putString("string", "My String").putInt("int", 5).commit()
        assertEquals(mapOf("string" to "My String", "int" to 5), prefs.all)
        assertEquals(setOf("string", "int"), updatedKeys)

        updatedKeys.clear()
        prefs.edit().clear().commit()
        assertEquals(0, prefs.all.size)
        assertEquals(setOf("string", "int"), updatedKeys)

        updatedKeys.clear()
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        prefs.edit().putString("newstring", "My New String").commit()
        assertEquals(0, updatedKeys.size)
    }

    private fun buildModel(): ObservableModel {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        val key = keyGenerator.generateKey()
        val model = ObservableModel.build(
            InstrumentationRegistry.getInstrumentation().targetContext,
            filePath = Paths.get(
                tempDir.toString(),
                "testdb"
            ).toString(),
            password = "testpassword",
        )
        model.registerType(20, Message::class.java)
        return model
    }

    @Before
    fun setupTempDir() {
        tempDir = Files.createTempDirectory("omtest")
    }

    @After
    fun deleteTempDir() {
        tempDir?.let {
            Files.walkFileTree(tempDir, object : FileVisitor<Path> {
                override fun preVisitDirectory(
                    dir: Path?,
                    attrs: BasicFileAttributes?
                ): FileVisitResult {
                    return FileVisitResult.CONTINUE;
                }

                override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                    return FileVisitResult.CONTINUE;
                }

                override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                    return FileVisitResult.CONTINUE;
                }
            })
        }
    }
}

internal data class Message(val body: String = "")