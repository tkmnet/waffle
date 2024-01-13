package jp.tkms.waffle.script.ruby.util;

import jp.tkms.waffle.data.log.message.ErrorLogMessage;
import jp.tkms.waffle.data.log.message.WarnLogMessage;
import jp.tkms.waffle.data.util.ResourceFile;
import jp.tkms.waffle.web.component.websocket.PushNotifier;
import org.jruby.RubyInstanceConfig;
import org.jruby.embed.EvalFailedException;
import org.jruby.embed.LocalContextScope;
import org.jruby.embed.LocalVariableBehavior;
import org.jruby.embed.ScriptingContainer;
import org.jruby.embed.osgi.internal.JRubyOSGiBundleClassLoader;
import org.jruby.exceptions.LoadError;
import org.jruby.exceptions.SystemCallError;
import org.jruby.util.ClassesLoader;
import org.jruby.util.JRubyClassLoader;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class RubyScript {
  private static AtomicInteger runningCount = new AtomicInteger(0);

  public static boolean hasRunning() {
    return (runningCount.get() > 0);
  }

  public static boolean process(Consumer<ScriptingContainer> process) {
    ScriptingContainerWrapper wrapper = new ScriptingContainerWrapper(process);
    wrapper.start();
    try {
      synchronized (wrapper) {
        wrapper.wait();
      }
    } catch (InterruptedException e) {
      ErrorLogMessage.issue(e);
    }
    return wrapper.isSuccess();

    /*
    boolean failed;
    do {
      synchronized (runningCount) {
        runningCount += 1;
      }
      failed = false;
      ScriptingContainer container = null;
      try {
        container = getScriptingContainer();
        try {
          container.runScriptlet(getInitScript());
          process.accept(container);
        } catch (EvalFailedException e) {
          ErrorLogMessage.issue(e);
        }
      } catch (SystemCallError | LoadError e) {
        failed = true;
        if (! e.getMessage().matches("Unknown error")) {
          failed = false;
        }
        WarnLogMessage.issue(e);
        try { Thread.sleep(1000); } catch (InterruptedException ex) { }
      } finally {
        releaseScriptingContainer(container);
        synchronized (runningCount) {
          runningCount -= 1;
        }
      }
    } while (failed);
     */
  }

  public static String debugReport() {
    return RubyScript.class.getSimpleName() + " : runningCount=" + runningCount.get();
  }

  public static String getInitScript() {
    return ResourceFile.getContents("/ruby_init.rb");
  }


  static class ScriptingContainerWrapper extends Thread {
    Consumer<ScriptingContainer> process;
    boolean isSuccess;

    public ScriptingContainerWrapper(Consumer<ScriptingContainer> process) {
      super(ScriptingContainerWrapper.class.getSimpleName());
      this.process = process;
      this.isSuccess = false;
    }

    boolean isSuccess() {
      return this.isSuccess;
    }

    @Override
    public void run() {
      boolean failed;
      do {
        runningCount.incrementAndGet();
        PushNotifier.sendRubyRunningStatus(true);
        failed = false;
        synchronized (runningCount) {
          //ScriptingContainer container = new ScriptingContainer(LocalContextScope.SINGLETHREAD, LocalVariableBehavior.TRANSIENT);
          ScriptingContainer container = new ScriptingContainer(LocalContextScope.SINGLETON, LocalVariableBehavior.TRANSIENT);
          try {
            try {
              container.runScriptlet(getInitScript());
              process.accept(container);
              isSuccess = true;
            } catch (EvalFailedException e) {
              ErrorLogMessage.issue(e);
            }
          } catch (SystemCallError | LoadError e) {
            failed = true;
            if (!e.getMessage().matches("Unknown error")) {
              failed = false;
            }
            WarnLogMessage.issue(e);
            try {
              Thread.sleep(1000);
            } catch (InterruptedException ex) {
            }
          } finally {
            try {
              container.finalize();
              System.gc();
            } catch (Throwable e) {
              ErrorLogMessage.issue(e);
            }
            container = null;
            runningCount.decrementAndGet();
            PushNotifier.sendRubyRunningStatus(false);
          }
        }
      } while (failed);
    }
  }
}
