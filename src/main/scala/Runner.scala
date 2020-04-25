package c

import java.util.concurrent.Executors
import java.{util => ju}

object Runner extends App {

  def computation(): Int = {
    val result = (1 to 10000).foldLeft(0){(acc, cur) =>
      if(cur % 2000 == 0){
        println(cur)
      }
      acc + cur
    }

    //Thread.sleep(2000)
    println("Main computation finished")
   result
  }

  implicit val th = new ThreadPool()
  val myFuture = th.submit(computation)

  Thread.sleep(2000)

  val myFuture2 = myFuture.map(_.toString).map(println)


  def a(a: Int) : Fu[String] = ???
  def b(s: String): Fu[Long] = ???
  def c(l: Long): Fu[ju.Date] = ???

  val result = for {
    first <- a(12)
    second <- b(first)
    third <- c(second)
  } yield third

}

import scala.concurrent.ExecutionContext.Implicits.global

class ThreadPool {
  private val th = Executors.newFixedThreadPool(10)

  def submit[T](f: () => T) : Fu[T] = {
    val future = new Fu[T]()
    val runnable = new MyRunnable(f, future)
    th.execute(runnable)
    future
  }

}

private class MyRunnable[T](f: () => T, future: Fu[T]) extends Runnable {
  override def run(): Unit = {
    val tempResult = f()
    future.complete(tempResult)
  }
}

class Fu[T] {
  type Listener = T => Unit
  private val listeners = new scala.collection.mutable.ListBuffer[Listener]()

  var result : Option[T] = None

  def complete(t: T): Unit = {
    listeners.foreach(l => l(t))
    result = Some(t)
  }

  def addOnCompleteListener(listener: Listener): Unit = {
    listeners += listener
  }

  def map[U](f: T => U)(implicit th: ThreadPool): Fu[U] = {
    result match {
      case Some(value) =>
        th.submit(() => f(value))
      case _ =>
        val newFuture = new Fu[U]()
        val listener: Listener = t => {
        val u = f(t)
        newFuture.complete(u)
        }
        addOnCompleteListener(listener)
        newFuture
    }
  }

  def flatMap[U](f: T => Fu[U]): Fu[U] = ???

}

