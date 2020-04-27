package async

import java.util.concurrent.Executors
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scala.util.control.NonFatal

private class StaticThreadPool(threadCount: Int) extends Execution {
  private val th = Executors.newFixedThreadPool(threadCount)

  def apply[T](f: () => T): Async[T] = {
    val future = new SimpleAsync[T]()
    val runnable = new MyRunnable(f, future)
    th.execute(runnable)
    future
  }
}

object ThreadPools {
  def fixCount(threadCount: Int): Execution = new StaticThreadPool(threadCount)

  def natural(parallelism: Double, offset: Int): Execution = {
    val available = Runtime.getRuntime().availableProcessors()
    new StaticThreadPool((available * parallelism + offset).toInt)
  }
}

private class MyRunnable[T](f: () => T, future: SimpleAsync[T])
    extends Runnable {

  override def run(): Unit = {
    val result =
      try {
        Success(f())
      } catch {
        case NonFatal(e) =>
          Failure(e)
      }
    future.complete(result)
  }
}

trait Execution {
  def apply[T](f: () => T): Async[T]
}

trait Async[T] {
  def map[U](f: T => U)(implicit execution: Execution): Async[U]
  def flatMap[U](f: T => Async[U])(implicit execution: Execution): Async[U]

  def onComplete(f: Try[T] => Unit): Unit
}

private class SimpleAsync[T] extends Async[T] {
  type Listener = Try[T] => Unit
  private val listeners = new scala.collection.mutable.ListBuffer[Listener]()

  var result: Option[Try[T]] = None

  def complete(t: Try[T]): Unit = {
    listeners.synchronized {
      listeners.foreach(l => l(t))
      result = Some(t)
    }
  }

  private def addOnCompleteListener(listener: Listener): Unit = {
    listeners += listener
  }

  def map[U](f: T => U)(implicit th: Execution): Async[U] =
    listeners.synchronized {
      result match {
        case Some(Success(value)) =>
          th.apply(() => f(value))
        case Some(Failure(e)) =>
          new FailedFuture[U](e)

        case None =>
          val newFuture = new SimpleAsync[U]()
          val listener: Listener = maybeT => {
            val maybeU = maybeT.map(f)
            newFuture.complete(maybeU)
          }
          addOnCompleteListener(listener)
          newFuture
      }
    }

  override def flatMap[U](f: T => Async[U])(
      implicit execution: Execution
  ): Async[U] = listeners.synchronized {
    result match {
      case Some(Success(value)) => f(value)
      case Some(Failure(e))     => new FailedFuture[U](e)
      case None =>
        val newFuture = new SimpleAsync[U]()
        val listener: Listener = maybeT => {
          val maybeU: Try[Async[U]] = maybeT.map(f)
          maybeU match {
            case Success(asy) => 
              asy.onComplete(newFuture.complete)

            case Failure(e) => 
              newFuture.complete(Failure[U](e))
          }
        }
        addOnCompleteListener(listener)
        newFuture
    }
  }

  override def onComplete(f: Try[T] => Unit): Unit = {
    listeners.synchronized {
      listeners += f
    }
  }
}

class FailedFuture[T](e: Throwable) extends Async[T] {

  override def map[U](f: T => U)(implicit th: Execution): Async[U] =
    new FailedFuture[U](e)

  override def flatMap[U](
      f: T => Async[U]
  )(implicit execution: Execution): Async[U] =
    new FailedFuture[U](e)

  override def onComplete(f: Try[T] => Unit): Unit = f(Failure(e))

}
