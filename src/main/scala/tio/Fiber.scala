package tio

import tio.TIO.AsyncDoneCallback

trait Fiber[+A] {
  def join(): TIO[A] = TIO.Join(this)

  private[tio] def onDone(done: AsyncDoneCallback[Any]): Fiber[A]
}
