<?xml version="1.0" encoding="UTF-8" ?>
<!-- Copyright 2015 1060 Research Ltd Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License 
	at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR 
	CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:nk="http://1060.org">
	<xsl:output method="xml" />
	<xsl:param name="config"/>
	<xsl:template match="/*">
		<div>
			<script type="text/javascript" src="/polestar/pub/multiselect.min.js"></script>
			<script type="text/javascript" src="/polestar/pub/bootstrap-datepicker.min.js"></script>
			<link href="/polestar/pub/css/bootstrap-datepicker3.min.css" rel="stylesheet" type="text/css"/>
			<script>
				<xsl:comment>
					$(function() {
						$('#multiselect').multiselect( { keepRenderingSort: true });
						$('.input-daterange').datepicker({
							format: "dd/mm/yyyy"
						});
						
						var intervalTimer;
						$("#restoreButton").click( function() {
						//$("#form").submit( function(e) {
							//e.preventDefault();
							$("#multiselect_to").find('option').prop('selected', true);
							var data=$("#form").serialize();
							console.log(data);
							$("#restoreButton").attr("disabled", "disabled");
							$("#statusDiv").css("display","block");
							$.post("/polestar/sensors/restore",data);
							intervalTimer=setInterval(timer, 500);
							var lastTime=new Date().getTime();
							var lastProgress=0;
							var lastRate=-1;
							var n=0;
							function timer() {
								$.get("/polestar/sensors/backup?action=status", function(d) {
									//console.log(d);
									var t=new Date().getTime();
									var rate=1000*(d.progress-lastProgress)/(t-lastTime);
									if (rate === Infinity)
									{
									}
									else
									{
										if (lastRate>0 &amp;&amp; n>3)
										{	lastRate=lastRate*0.98+rate*0.02;
										}
										else
										{	lastRate=rate;
										}
									}
									n++;
									var timeRemaining=(d.progressTotal-d.progress)/lastRate;
									//console.log(rate+" "+lastRate+" "+(d.progressTotal-d.progress));
									var mins=Math.floor(timeRemaining/60);
									var secs=Math.round(timeRemaining-mins*60);
									lastProgress=d.progress;
									lastTime=t;
									var percent=100*d.progress/d.progressTotal;
									$("#statusProgress").css("width",percent+"%");
									if (d.msg.length>0)
									{	$("#statusMessage").html(d.msg);
									}
									else
									{	var msg="Restoring "+d.progress+" of "+d.progressTotal+" ("+Math.round(percent)+"%) Time remaining "+mins+"min, "+secs+"sec";
										$("#statusMessage").html(msg);
									}
									if (d.state!="RESTORE_INPROGRESS")
									{	clearInterval(intervalTimer);
									}	
								},"json");
							}
						});
					});
				</xsl:comment>
			</script>

	<div class="container">
		<div class="row">

			<div class="alert alert-info">
				<h2 style="margin-top: 0;">
					<span class="glyphicon glyphicon-cloud-download"></span>
					Sensor Data Restore
				</h2>
				<p>This page summarises the contents of the backup to be restored. Each sensor is listed with the number of records
					it contains and its status - whether it overlaps with existing sensor data or not and whether sensor definition
					exists for data being added (it is better to add sensor definitions before import) _ERRORS_ are the combined
					errors from all exported sensors.
				</p>
				<p>By default the whole contents of the backup file are restored. However you can select which sensors and also
				constrain the time range for the restore.
				</p>
				<p>
					Select a restore mode. Currently only
					<i>Replace</i>
					is supported. Replace will delete existing sensor data between
					time range and replace it with data from backup.
				</p>
			</div>
		</div>

		<div class="row">
			<div class="panel panel-default">
				<div class="panel-heading">
					<h3 class="panel-title">Backup File Contents</h3>
				</div>
				<div class="panel-body">
					<xsl:call-template name="contents" />
				</div>
			</div>
		</div>


		<div class="row">
			<div class="panel panel-default">
				<div class="panel-heading">
					<h3 class="panel-title">Select What to Restore</h3>
				</div>
				<div class="panel-body">
					<xsl:call-template name="filter" />
				</div>
			</div>
		</div>




	</div>
		</div>


	</xsl:template>

	<xsl:template name="filter">

		<form id="form">

			<label class="control-label" for="multiselect">Sensors</label>
			<div class="row">

				<div class="col-xs-5">
					<select name="from" id="multiselect" class="form-control" size="8" multiple="multiple">_
						
					</select>
				</div>
				<div class="col-xs-2">
					<button type="button" id="multiselect_rightAll" class="btn btn-block">
						<span class="glyphicon glyphicon-forward"></span>
					</button>
					<button type="button" id="multiselect_rightSelected" class="btn btn-block">
						<span class="glyphicon glyphicon-chevron-right"></span>
					</button>
					<button type="button" id="multiselect_leftSelected" class="btn btn-block">
						<span class="glyphicon glyphicon-chevron-left"></span>
					</button>
					<button type="button" id="multiselect_leftAll" class="btn btn-block">
						<span class="glyphicon glyphicon-backward"></span>
					</button>
				</div>
				<div class="col-xs-5">
					<select name="to" id="multiselect_to" class="form-control" size="8" multiple="multiple">
						<xsl:for-each select="sensor[id!='_ERRORS_']">
							<option value="{id}">
								<xsl:value-of select="id" />
							</option>
						</xsl:for-each>
					</select>
				</div>

			</div>

			<div class="form-group">
				<label class="control-label" for="from">Date Range</label>
				<div class="row">
					<div class="col-xs-12">
						<div class="input-daterange input-group" id="datepicker">
							<input type="text" class="input-sm form-control" name="start" value="{$config/*/oldest}" />
							<span class="input-group-addon">to</span>
							<input type="text" class="input-sm form-control" name="end" value="{$config/*/newest}" />
						</div>
					</div>
				</div>
			</div>

			<div class="row">
				<div class="col-xs-3">
					<label>Mode</label>
				</div>
				<div class="col-xs-9">
					<select class="form-control" name="mode">
						<option>Replace</option>
						<option>Selective</option>
						<option>Add</option>
					</select>
				</div>

				<div class="form-group">
					<input type="hidden" name="fileURI" value="{$config/*/fileURI}" />
					<!--<input type="hidden" name="first" value="{$config/*/oldestRaw}" />
					<input type="hidden" name="last" value="{$config/*/newestRaw}" />-->
					<input type="hidden" name="action" value="confirm" />
				</div>


			</div>

			<div class="row" style="margin-top: 1em;">

				<div class="col-xs-3">
					<button id="restoreButton" class="btn btn-primary">Restore</button>
				</div>
				<div class="col-xs-9" id="statusDiv" style="display:none;">
					<div class="progress" style="margin-bottom: 0">
						<div class="progress-bar" role="progressbar" style="width: 0%;" id="statusProgress" />
					</div>
					<div id="statusMessage" />
				</div>
			</div>
		</form>

	</xsl:template>


	<xsl:template name="contents">
		<table class="table table-condensed table-striped">
			<thead>
				<tr>
					<th>Sensor</th>
					<th>Status</th>
					<th style="text-align:right">Data points</th>

				</tr>
			</thead>
			<tbody>
				<xsl:for-each select="sensor">
					<tr>
						<td>
							<xsl:value-of select="id" />
						</td>

						<td>
							<xsl:choose>
								<xsl:when test="exists='false'">
									<span class="label label-danger">No Sensor Definition</span>
								</xsl:when>
								<xsl:when test="overlap='true'">
									<span class="label label-danger">Overlap</span>
								</xsl:when>
								<xsl:otherwise>
									<span class="label label-success">OK</span>
								</xsl:otherwise>
							</xsl:choose>
						</td>
						<td style="text-align:right">
							<xsl:value-of select="format-number(count,'###,###')" />
						</td>
					</tr>
				</xsl:for-each>
			</tbody>
			<tfoot style="border-top: 2px black solid; border-bottom: 2px black solid;">
				<tr>
					<td>Total</td>
					<td>
					</td>
					<td style="text-align:right">
						<xsl:value-of select="format-number(count,'###,###')" />
					</td>
				</tr>
			</tfoot>
		</table>
	</xsl:template>

</xsl:stylesheet>