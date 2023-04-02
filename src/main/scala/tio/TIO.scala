package tio

sealed trait TIO[+A] {
  def flatMap[B](f: A => TIO[B]): TIO[B] = TIO.FlatMap(this, f)
  def map[B](f: A => B): TIO[B] = flatMap(a => TIO.succeed(f(a)))

  def *>[B](that: TIO[B]): TIO[B] = flatMap(_ => that)

  def recover[B >: A](f: Throwable => TIO[B]): TIO[B] = TIO.Recover(this, f)
}

object TIO {

  case class Effect[+A](a: () => A) extends TIO[A]
  case class FlatMap[A, B](tio: TIO[A], f: A => TIO[B]) extends TIO[B]

  case class Fail[A](e: Throwable) extends TIO[A]
  case class Recover[A](tio: TIO[A], f: Throwable => TIO[A]) extends TIO[A]

  def succeed[A](a: A): TIO[A] = Effect(() => a)
  def effect[A](a: => A): TIO[A] = Effect(() => a)
  def fail[A](throwable: Throwable): TIO[A] = Fail(throwable)
}
