package com.example

import java.io.{IOException, File}
import java.sql.{Connection, DriverManager, ResultSet}

import net.gree.aurora.scala.application.{AuroraTableNameService, AuroraShardingService}
import net.gree.aurora.scala.domain.cluster.AbstractClusterIdResolver
import net.gree.aurora.scala.domain.datasource.JDBCDataSource
import net.gree.aurora.scala.domain.hint.Hint
import net.gree.aurora.scala.domain.tablename.AbstractTableNameResolver
import org.sisioh.dddbase.core.lifecycle.EntityNotFoundException
import org.sisioh.dddbase.core.lifecycle.sync.SyncEntityIOContext

import scala.util.Try

object Main extends App {

  case class User(id: Int, name: String)

  // リゾルバを定義する
  private val clusterIdResolver = new AbstractClusterIdResolver[Int]("cluster") {
    override protected def getSuffixName(userIdHint: Hint[Int], clusterSize: Int): String =  {
      userIdHint.value % 10 match {
        case n if n < 5 => "1"
        case _          => "2"
      }
    }
  }

  // AuroraShardingServiceを初期化する
  private val auroraShardingService = AuroraShardingService(clusterIdResolver, new File("./conf/application.conf"))

  private implicit val dataSourceRepository = auroraShardingService.dataSourceRepository.get
  private implicit val ctx = SyncEntityIOContext

  // テーブル名の解決
  private val tableNameResolver = new AbstractTableNameResolver[Int]("user") {
    override protected def getSuffixName(userIdHint: Int): String = {
      "_" + "%d".format(userIdHint % 10)
    }
  }
  private val auroraTableNameService = AuroraTableNameService(tableNameResolver, new File("./conf/application.conf"))

  private def findByUserId(userId: Int): Try[User] = {
    // テーブル名を解決する
    val table = auroraTableNameService.resolveByHint(userId).get

    // sharding-config-idとcluster-group-idとヒントを引数に与えてclusterを解決する
    auroraShardingService.resolveClusterByHint("database", "main", Hint(userId)).map { cluster =>
      // スレーブはランダム選択
      val selector = cluster.createDataSourceSelectorAsRandom
      // スレーブデータソースを選択する
      val dataSource = selector.selectDataSourceAsJDBC().get
      // 書き込み用途でマスターを利用する場合は、以下。
      // val dataSource = cluster.masterDataSource

      // データソースからコネクションを取得する
      val connection = getConnection(dataSource)

      // コネクションを使ってSQLを発行する
      val st = connection.createStatement
      val rs = st.executeQuery("SELECT * FROM " + table.name + " WHERE id = " + userId)
      if (rs.next())
        convertToModel(rs)
      else
        throw new IOException("entity is not found.")
    }
  }

  private def getConnection(dataSource: JDBCDataSource): Connection = {
    val url = dataSource.url
    val userName = dataSource.userName
    val password = dataSource.password
    Class.forName(dataSource.driverClassName)
    DriverManager.getConnection(url, userName, password)
  }

  private def convertToModel(resultSet: ResultSet): User =
    User(
      id = resultSet.getInt("id"),
      name = resultSet.getString("name")
    )

  println(findByUserId(1000))
  println(findByUserId(1055))
  println(findByUserId(9999))

}
