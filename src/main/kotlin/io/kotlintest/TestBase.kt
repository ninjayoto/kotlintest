package io.kotlintest

import io.kotlintest.matchers.Matchers
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import org.junit.runners.model.TestTimedOutException
import java.io.Closeable
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@RunWith(KTestJUnitRunner::class)
abstract class TestBase : Matchers {

  private val closeablesInReverseOrder = LinkedList<Closeable>()

  // TODO change to true, because one instance per test is a safer default
  open val oneInstancePerTest = false

  // the root test suite which uses the simple name of the class as the name of the suite
  // spec implementations will add their tests to this suite
  internal val root = TestSuite(javaClass.simpleName, ArrayList<TestSuite>(), ArrayList<TestCase>())

  // returns a jUnit Description for the currently registered tests
  internal fun getDescription(): Description = descriptionForSuite(root)

  internal fun run(notifier: RunNotifier) {
    if (oneInstancePerTest) runOneInstancePerTest(notifier)
    else runSharedInstance(notifier)
  }

  protected fun <T: Closeable>autoClose(closeable: T): T {
    closeablesInReverseOrder.addFirst(closeable)
    return closeable
  }

  // this should live in some matchers class, but can't inline in an interface :(
  inline fun <reified T> shouldThrow(thunk: () -> Any): T {
    val e = try {
      thunk()
      null
    } catch (e: Throwable) {
      e
    }

    if (e == null)
      throw TestFailedException("Expected exception ${T::class.qualifiedName} but no exception was thrown") // TODO move to try block
    else if (e.javaClass.name != T::class.qualifiedName)
      throw TestFailedException("Expected exception ${T::class.qualifiedName} but ${e.javaClass.name} was thrown")
    else
      return e as T
  }


  private fun runOneInstancePerTest(notifier: RunNotifier): Unit {
    val testCount = listTests(root).size // TODO move to TestSuite
    for (k in (0..testCount - 1)) {
      val instance = javaClass.newInstance()
      val testcase = listTests(instance.root)[k]
      if (testcase.active() && isTagged(testcase)) {
        val desc = descriptionForTest(testcase)
        instance.beforeAll()
        instance.afterEach()
        runTest(testcase, notifier, desc!!)
        instance.afterEach()
        instance.performAfterAll()
      }
    }
  }

  private fun runSharedInstance(notifier: RunNotifier): Unit {
    beforeAll()
    val tests = listTests(root)
    tests.filter { isTagged(it) }.filter { it.active() }.forEach { testcase ->
      val desc = descriptionForTest(testcase)
      beforeEach()
      runTest(testcase, notifier, desc!!)
      afterEach()
    }
    performAfterAll()
  }

  // TODO move to TestStuite (and remove `get` prefix)
  private fun listTests(suite: TestSuite): List<TestCase> =
          suite.cases + suite.nestedSuites.flatMap { suite -> listTests(suite) }

  private fun isTagged(testcase: TestCase): Boolean {
    val systemTags = (System.getProperty("testTags") ?: "").split(',')
    return systemTags.isEmpty() || testcase.config.tags.isEmpty() || systemTags.intersect(testcase.config.tags).isNotEmpty()
  }

  private fun runTest(testcase: TestCase, notifier: RunNotifier, description: Description): Unit {
    val executor =
            if (testcase.config.threads < 2) Executors.newSingleThreadExecutor()
            else Executors.newFixedThreadPool(testcase.config.threads)
    notifier.fireTestStarted(description)
    for (j in 1..testcase.config.invocations) {
      executor.submit {
        try {
          testcase.test()
        } catch(e: Throwable) {
          notifier.fireTestFailure(Failure(description, e))
        }
      }
    }
    notifier.fireTestFinished(description)
    executor.shutdown()
    val timeout = testcase.config.timeout
    val terminated = executor.awaitTermination(timeout.amount, timeout.timeUnit)
    if (!terminated) {
      notifier.fireTestFailure(Failure(description, TestTimedOutException(timeout.amount, timeout.timeUnit)))
    }
  }

  internal fun descriptionForSuite(suite: TestSuite): Description {
    val desc = Description.createSuiteDescription(suite.name.replace('.', ' '))
    for (nestedSuite in suite.nestedSuites) {
      desc.addChild(descriptionForSuite(nestedSuite))
    }
    for (case in suite.cases) {
      desc.addChild(descriptionForTest(case))
    }
    return desc
  }

  internal fun descriptionForTest(case: TestCase): Description? {
    val text = if (case.config.invocations < 2) case.name else case.name + " (${case.config.invocations} invocations)"
    return Description.createTestDescription(case.suite.name.replace('.', ' '), text)
  }

  open fun beforeAll(): Unit {
  }

  open fun beforeEach(): Unit {
  }

  open fun afterEach(): Unit {
  }

  open fun afterAll(): Unit {
  }

  internal fun performAfterAll() {
    afterAll()
    closeablesInReverseOrder.forEach { it.close() }
  }
}