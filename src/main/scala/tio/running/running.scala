package tio.running

import tio.TIO.{AsyncDoneCallback, AsyncTask}
import tio.{TIO, TIOApp}

import scala.util.control.NonFatal
import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import java.time.Instant
import java.util.{Timer, TimerTask}
import scala.util.Success

object SequenceEffects extends TIOApp {
  def run = {
    for {
      _ <- TIO.effect(println("running first effect"))
      _ <- TIO.effect(println("running second effect"))
    } yield ()
  }
}

object Console {
  def putStrLn(str: => String) = TIO.effect(println(str))
}

import Console._

object ExampleWithThrow extends TIOApp {
  override def run = {
    for {
      _ <- putStrLn("running first effect")
      _ <- TIO.effect(throw new RuntimeException)
      _ <- putStrLn("running second effect")
    } yield ()
  }
}

object FailAndRecover extends TIOApp {
  def run = {
    (for {
      _ <- putStrLn("running first effect")
      _ <- TIO.fail(new RuntimeException)
      _ <- putStrLn("second effect - will not run")
    } yield ()).recover { case NonFatal(e) =>
      putStrLn(s"recovered from failure: ${e.getClass.getName}")
    }
  }
}

object Foreach10k extends TIOApp {
  def run = TIO.foreach(1 to 10000)(i => TIO.effect(println(i)))
}

object Clock {
  // Use EffectAsync to implement a non-blocking "sleep"
  private val timer = new Timer("TIO-Timer", /* isDaemon */ true)

  def sleep[A](duration: Duration): TIO[Unit] =
    TIO.effectAsync { onComplete: AsyncDoneCallback[Unit] =>
      timer.schedule(
        new TimerTask {
          override def run(): Unit = onComplete(Success(()))
        },
        duration.toMillis
      )
    }
}
import Clock._

object SleepExample extends TIOApp {
  def run = {
    for {
      _ <- TIO.effect(
        println(
          s"[${Instant.now}] running first effect on ${Thread.currentThread.getName}"
        )
      )
      _ <- sleep(2.seconds)
      _ <- TIO.effect(
        println(
          s"[${Instant.now}] running second effect on ${Thread.currentThread.getName}"
        )
      )
    } yield ()
  }
}
