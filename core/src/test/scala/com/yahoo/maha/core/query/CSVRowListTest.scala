// Copyright 2017, Yahoo Holdings Inc.
// Licensed under the terms of the Apache License 2.0. Please see LICENSE file in project root for terms.
package com.yahoo.maha.core.query

import java.io.{File, FileNotFoundException}
import java.nio.file.{Files, StandardOpenOption}

import com.yahoo.maha.core.RequestModel
import com.yahoo.maha.core.query.oracle.BaseOracleQueryGeneratorTest
import com.yahoo.maha.core.request.ReportingRequest
import com.yahoo.maha.report.FileRowCSVWriterProvider

/**
 * Created by hiral on 1/25/16.
 */
class CSVRowListTest extends BaseOracleQueryGeneratorTest with BaseRowListTest {

  def query : Query = {
    val jsonString = s"""{
                          "cube": "k_stats",
                          "selectFields": [
                            {"field": "Campaign ID"},
                            {"field": "Impressions"},
                            {"field": "Campaign Name"},
                            {"field": "Campaign Status"},
                            {"field": "CTR"}
                          ],
                          "filterExpressions": [
                            {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"},
                            {"field": "Advertiser ID", "operator": "=", "value": "213"},
                            {"field": "Campaign Name", "operator": "=", "value": "MegaCampaign"}
                          ],
                          "sortBy": [
                            {"field": "Campaign Name", "order": "Asc"}
                          ],
                          "paginationStartIndex":-1,
                          "rowsPerPage":100
                        }"""

    val request: ReportingRequest = getReportingRequestSync(jsonString)
    val registry = getDefaultRegistry()
    val requestModel = RequestModel.from(request, registry)
    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))


    val queryPipelineTry = generatePipeline(requestModel.toOption.get)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[OracleQuery]
  }

  def queryWithAlias : Query = {
    val jsonString = s"""{
                          "cube": "k_stats",
                          "selectFields": [
                            {"field": "Campaign ID", "alias":"CampaignID"},
                            {"field": "Impressions", "alias":"Impressions"},
                            {"field": "Campaign Name", "alias":"CampaignName"},
                            {"field": "Campaign Status", "alias":"CampaignStatus"},
                            {"field": "CTR", "alias":"CTR"}
                          ],
                          "filterExpressions": [
                            {"field": "Day", "operator": "between", "from": "$fromDate", "to": "$toDate"},
                            {"field": "Advertiser ID", "operator": "=", "value": "213"},
                            {"field": "Campaign Name", "operator": "=", "value": "MegaCampaign"}
                          ],
                          "sortBy": [
                            {"field": "Campaign Name", "order": "Asc"}
                          ],
                          "paginationStartIndex":-1,
                          "rowsPerPage":100
                        }"""

    val request: ReportingRequest = getReportingRequestAsync(jsonString)
    val registry = getDefaultRegistry()
    val requestModel = RequestModel.from(request, registry)

    assert(requestModel.isSuccess, requestModel.errorMessage("Building request model failed"))


    val queryPipelineTry = generatePipeline(requestModel.toOption.get)
    assert(queryPipelineTry.isSuccess, queryPipelineTry.errorMessage("Fail to get the query pipeline"))

    queryPipelineTry.toOption.get.queryChain.drivingQuery.asInstanceOf[OracleQuery]
  }

  test("successfully construct csv row list with header") {
    val tmpPath = new File("target").toPath
    val tmpFile = Files.createTempFile(tmpPath, "pre", "suf").toFile
    tmpFile.deleteOnExit()
    val rowListWithHeaders : CSVRowList = new CSVRowList(query, FileRowCSVWriterProvider(tmpFile), true)
    assert(rowListWithHeaders.columnNames === IndexedSeq("Campaign ID", "Impressions", "Campaign Name", "Campaign Status", "CTR", "TOTALROWS"))
    assert(rowListWithHeaders.isEmpty)

    rowListWithHeaders.withLifeCycle {
      val row = rowListWithHeaders.newRow

      row.addValue("Campaign ID", java.lang.Integer.valueOf(1))
      row.addValue("Impressions", java.lang.Integer.valueOf(2))
      row.addValue("Campaign Name", "\"name\"")
      row.addValue("Campaign Status", "o,n")
      row.addValue("CTR", java.lang.Double.valueOf(1.11D))
      row.addValue("TOTALROWS", java.lang.Integer.valueOf(1))
      assert(row.getValue("Campaign ID") === 1)
      assert(row.getValue("Impressions") === 2)
      assert(row.getValue("Campaign Name") === "\"name\"")
      assert(row.getValue("Campaign Status") === "o,n")
      assert(row.getValue("CTR") === 1.11D)
      assert(row.getValue("TOTALROWS") === 1)

      assert(row.aliasMap.size === 6)
      assert(row.getValue(0) === 1)
      assert(row.getValue(1) === 2)
      assert(row.getValue(2) === "\"name\"")
      assert(row.getValue(3) === "o,n")
      assert(row.getValue(4) === 1.11D)
      assert(row.getValue(5) === 1)

      rowListWithHeaders.addRow(row)
    }
    
    val csvLines = scala.io.Source.fromFile(tmpFile, "UTF-8").getLines()
    var count = 0
    csvLines.foreach {
      line =>
        count += 1
        if(count == 1) {
          assert(line === "Campaign ID,Impressions,Campaign Name,Campaign Status,CTR,TOTALROWS")
        }
        if(count == 2) {
          assert(line === "1,2,\"\"\"name\"\"\",\"o,n\",1.11,1", line)
        }
    }
  }

  test("successfully construct csv row list without header") {
    val tmpPath = new File("target").toPath
    val tmpFile = Files.createTempFile(tmpPath, "pre", "suf").toFile
    tmpFile.deleteOnExit()
    val rowListWithOutHeaders : CSVRowList = new CSVRowList(query, FileRowCSVWriterProvider(tmpFile), false)
    assert(rowListWithOutHeaders.columnNames === IndexedSeq("Campaign ID", "Impressions", "Campaign Name", "Campaign Status", "CTR", "TOTALROWS"))
    assert(rowListWithOutHeaders.isEmpty)

    rowListWithOutHeaders.withLifeCycle {
      val row = rowListWithOutHeaders.newRow

      row.addValue("Campaign ID", java.lang.Integer.valueOf(1))
      row.addValue("Impressions", java.lang.Integer.valueOf(2))
      row.addValue("Campaign Name", "\"name\"")
      row.addValue("Campaign Status", "o,n\n")
      row.addValue("CTR", java.lang.Double.valueOf(1.11D))
      row.addValue("TOTALROWS", java.lang.Integer.valueOf(1))
      assert(row.getValue("Campaign ID") === 1)
      assert(row.getValue("Impressions") === 2)
      assert(row.getValue("Campaign Name") === "\"name\"")
      assert(row.getValue("Campaign Status") === "o,n\n")
      assert(row.getValue("CTR") === 1.11D)
      assert(row.getValue("TOTALROWS") === 1)

      assert(row.aliasMap.size === 6)
      assert(row.getValue(0) === 1)
      assert(row.getValue(1) === 2)
      assert(row.getValue(2) === "\"name\"")
      assert(row.getValue(3) === "o,n\n")
      assert(row.getValue(4) === 1.11D)
      assert(row.getValue(5) === 1)

      rowListWithOutHeaders.addRow(row)
    }

    val csvLines = scala.io.Source.fromFile(tmpFile, "UTF-8").getLines()
    var count = 0
    csvLines.foreach {
      line =>
        count += 1
        if(count == 1) {
          assert(line === "1,2,\"\"\"name\"\"\",\"o,n")
        }
        if(count == 2) {
          assert(line === "\",1.11,1")
        }
    }
    assert(count == 2)
  }

  test("successfully construct csv row list with header with aliases") {
    val tmpPath = new File("target").toPath
    val tmpFile = Files.createTempFile(tmpPath, "pre", "suf").toFile
    tmpFile.deleteOnExit()
    Files.write(tmpFile.toPath, Array[Byte](1,2,3,4,5), StandardOpenOption.TRUNCATE_EXISTING) // Clear file
    val rowListWithHeaders : CSVRowList = new CSVRowList(queryWithAlias, FileRowCSVWriterProvider(tmpFile), true)
    assert(rowListWithHeaders.isEmpty)

    rowListWithHeaders.withLifeCycle {
      val row = rowListWithHeaders.newRow

      row.addValue("Campaign ID", java.lang.Integer.valueOf(1))
      row.addValue("Impressions", java.lang.Integer.valueOf(2))
      row.addValue("Campaign Name", "\"name\"")
      row.addValue("Campaign Status", "o,n")
      row.addValue("CTR", java.lang.Double.valueOf(1.11D))

      assert(row.getValue("Campaign ID") === 1)
      assert(row.getValue("Impressions") === 2)
      assert(row.getValue("Campaign Name") === "\"name\"")
      assert(row.getValue("Campaign Status") === "o,n")
      assert(row.getValue("CTR") === 1.11D)

      assert(row.aliasMap.size === 5)
      assert(row.getValue(0) === 1)
      assert(row.getValue(1) === 2)
      assert(row.getValue(2) === "\"name\"")
      assert(row.getValue(3) === "o,n")
      assert(row.getValue(4) === 1.11D)

      rowListWithHeaders.addRow(row)

    }
    val csvLines = scala.io.Source.fromFile(tmpFile, "UTF-8").getLines()
    var count = 0
    var checkedHeader = false
    csvLines.foreach {
      line =>
        count += 1
        if(count == 1) {
          assert(line === "CampaignID,Impressions,CampaignName,CampaignStatus,CTR")
          checkedHeader = true
        }
        if(count == 2) {
          assert(line === "1,2,\"\"\"name\"\"\",\"o,n\",1.11", line)
        }
    }
    assert(checkedHeader)
  }

  test("fail to construct file csv row list with no write perm") {
    val tmpFile= new File("/blah")
    val rowListWithHeaders : CSVRowList = new CSVRowList(query, FileRowCSVWriterProvider(tmpFile), true)
    val thrown = intercept[FileNotFoundException] {
      rowListWithHeaders.withLifeCycle {
        println("hello")
      }
    }
    assert(thrown.getMessage === "/blah (Permission denied)")
  }
}
