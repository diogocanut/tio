package tio

import tio.TIO.AsyncDoneCallback

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._

trait Runtime {
  def unsafeRunAsync[A](tio: TIO[A])(callback: Try[A] => Unit): Unit

  def unsafeRunSync[A](tio: TIO[A], timeout: Duration = Duration.Inf): Try[A] =
    Await.ready(unsafeRunToFuture(tio), timeout).value.get

  def unsafeRunToFuture[A](tio: TIO[A]): Future[A] = {
    val promise = Promise[A]()
    unsafeRunAsync(tio)(promise.tryComplete)
    promise.future
  }

}

object Runtime extends Runtime {

  private val executor = Executor.fixed(16, "tio-default")

  override def unsafeRunAsync[A](
      tio: TIO[A]
  )(callback: Try[A] => Unit): Unit = {
    new FiberRuntime(tio)
      .onDone(callback.asInstanceOf[AsyncDoneCallback[Any]])
      .start()
  }

  private class FiberRuntime(tio: TIO[Any]) extends Fiber[Any] {
    type Callbacks = Set[AsyncDoneCallback[Any]]
    private val joined = new AtomicReference[Callbacks](Set.empty)

    private val result = new AtomicReference[Option[Try[Any]]](None)

    def onDone(done: AsyncDoneCallback[Any]): FiberRuntime = {
      joined.updateAndGet(_ + done)
      result.get.foreach(done)
      this
    }

    private def fiberDone(res: Try[Any]): Unit = {
      result.set(Some(res))
      joined.get.foreach(_(res))
    }

    def start(): Unit = {
      eval(tio)(fiberDone)
    }

    private def eval(tio: TIO[Any])(done: Try[Any] => Unit): Unit = {
      executor.submit {
        tio match {
          case TIO.Effect(a) =>
            done(Try(a()))

          case TIO.EffectAsync(callback) => callback(done)

          case TIO.FlatMap(tio, f: (Any => TIO[Any])) =>
            eval(tio) {
              case Success(res) => eval(f(res))(done)
              case Failure(e)   => done(Failure(e))
            }

          case TIO.Fail(e) => done(Failure(e))

          case TIO.Recover(tio, f) =>
            eval(tio) {
              case Failure(e) => eval(f(e))(done)
              case success    => done(success)
            }

          case TIO.EffectAsync(callback) =>
            callback(done)

          case TIO.Fork(tio) =>
            val fiber = new FiberRuntime(tio)
            fiber.start()
            done(Success(fiber))

          case TIO.Join(fiber) =>
            fiber.onDone(done)

        }
      }
    }
  }
}

trait TIOApp {
  def run: TIO[Any]
  final def main(args: Array[String]): Unit = Runtime.unsafeRunSync(run).get
}
