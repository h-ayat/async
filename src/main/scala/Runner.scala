package async

import java.util.concurrent.Executors
import java.{util => ju}
import scala.util.Success
import scala.util.Failure

object Runner extends App {

  def computation(): Int = {
    val result = (1 to 10000).foldLeft(0) { (acc, cur) =>
      if (cur % 2000 == 0) {
        println(cur)
      }
      acc + cur
    }

    //Thread.sleep(2000)
    println("Main computation finished")
    result
  }

  implicit val exec = ThreadPools.fixCount(10)
  val f = exec.apply(computation)
  val f2 = f.map(_.toString)


  def getId(cell: String) : Async[String] = ???

  def getNameById(id: String): Async[String] = ???

  val cell = "0912111111"

  val asyncId = getId(cell)
  val asyncName = asyncId.flatMap(getNameById)

  asyncName.onComplete{
    case Success(value) => println("Hoooooooooora")
    case Failure(e) => println(e)
  }
  /*
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
   */


}
