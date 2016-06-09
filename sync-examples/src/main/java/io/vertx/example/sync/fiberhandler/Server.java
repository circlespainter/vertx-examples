package io.vertx.example.sync.fiberhandler;

import co.paralleluniverse.fibers.Fiber;
import co.paralleluniverse.fibers.FiberForkJoinScheduler;
import co.paralleluniverse.fibers.Suspendable;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.example.util.Runner;
import io.vertx.ext.sync.Sync;
import io.vertx.ext.sync.SyncVerticle;

import java.io.PrintStream;
import java.lang.reflect.Field;

import static io.vertx.ext.sync.Sync.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Server extends SyncVerticle {
    private static final String ADDRESS = "some-address";
    private static final PrintStream o = System.out;

    private static final String FIBER_SCHEDULER_CONTEXT_KEY;

    static {
        final Field f;
        try {
            f = Sync.class.getDeclaredField("FIBER_SCHEDULER_CONTEXT_KEY");
            f.setAccessible(true);
            FIBER_SCHEDULER_CONTEXT_KEY = (String) f.get(null);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /*
    Convenience method so you can run it in your IDE
    Note if in IDE need to edit run settings and add something like:
    -javaagent:/Users/fabio/.m2/repository/co/paralleluniverse/quasar-core/0.7.5/quasar-core-0.7.5-jdk8.jar
     */
    public static void main(String[] args)  {
        Runner.runExample(Server.class);
    }

    @Suspendable
    @Override
    public void start() throws Exception {
        final EventBus eb = vertx.eventBus();

        // Replace fiber scheduler with actual one (e.g. ForkJoin). If you comment this out you'll see the "direct/inline"
        // trace, where the fiber is started and executed in the same thread.
        vertx.getOrCreateContext().put(FIBER_SCHEDULER_CONTEXT_KEY, new FiberForkJoinScheduler("vertx-sync", Runtime.getRuntime().availableProcessors()));

        eb.consumer(ADDRESS).handler(msg -> {
            o.println("[VertxHandler] StackTrace:");
            new Throwable().printStackTrace(o);
            final Object vertxSyncFiberSchedulerAttrValue = vertx.getOrCreateContext().get(FIBER_SCHEDULER_CONTEXT_KEY);
            printScheduler("[VertxHandler] ", vertxSyncFiberSchedulerAttrValue);
            o.println("[VertxHandler] replying");
            msg.reply("");
        });

        // If you want to do sync stuff in an async handler it must be transformed to a fiber handler
        vertx.createHttpServer().requestHandler(fiberHandler(req -> {
            o.println("[HTTPReqHandler] StackTrace:");
            new Throwable().printStackTrace(o);
            // Send a message to address and wait for a reply
            final Message<String> reply = awaitResult(h -> eb.send(ADDRESS, "blah", h));
            final Object vertxSyncFiberSchedulerAttrValue = vertx.getOrCreateContext().get(FIBER_SCHEDULER_CONTEXT_KEY);

            printScheduler("[HTTPReqHandler] ", vertxSyncFiberSchedulerAttrValue);
            o.println("[HTTPReqHandler] Got reply.");

            req.response().end("Check stdout!");
        })).listen(8080, "localhost");
    }

    private static void printScheduler(String prefix, Object vertxSyncFiberSchedulerAttrValue) {
        o.println(prefix + "vertx-sync FIBER_SCHEDULER_CONTEXT_KEY attribute value: " + (vertxSyncFiberSchedulerAttrValue != null ? vertxSyncFiberSchedulerAttrValue.getClass().getName() : "<NOT SET>"));
        o.println(prefix  + (Fiber.currentFiber() == null ? "Not running in a fiber" : "Running in a fiber, scheduler class: " + Fiber.currentFiber().getScheduler().getClass().getName()));
    }
}
