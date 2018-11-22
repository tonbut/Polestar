<?xml version="1.0" encoding="UTF-8" ?>
<!-- Copyright 2015 1060 Research Ltd Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License 
	at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR 
	CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:nk="http://1060.org">
	<xsl:output method="xml" />
	<xsl:param name="config"/>
	<xsl:template match="/*">
		<div>
			<script>
				<xsl:comment>
					$(function() {
						var intervalTimer;
						$("#restoreButton").click( function() {
							var data=$("#form").serialize();
							console.log(data);
							$("#restoreButton").attr("disabled", "disabled");
							$("#statusDiv").css("display","block");
							$.post("/polestar/sensors/restore",data);
							intervalTimer=setInterval(timer, 500);
							
							function timer() {
								$.get("/polestar/sensors/backup?action=status", function(d) {
									//console.log(d);
									var percent=100*d.progress/d.progressTotal;
									$("#statusProgress").css("width",percent+"%");
									if (d.msg.length>0)
									{	$("#statusMessage").html(d.msg);
									}
									else
									{	var msg="Restoring "+d.progress+" of "+d.progressTotal+" ("+Math.round(percent)+"%)";
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
						<h2 style="margin-top: 0;"><span class="glyphicon glyphicon-cloud-download"></span> Sensor Data Restore</h2>
						<p>This page summarises the contents of the backup to be restored. Each sensor is listed with the number of records
						it contains and its status - whether it overlaps with existing sensor data or not and whether sensor definition
						exists for data being added (it is better to add sensor definitions before import) </p>
						<p>From and To fields show time range contained with the backup data.</p>
						<p>Select a restore mode. Currently only <i>Replace</i> is supported. Replace will delete existing sensor data between
						time range and replace it with data from backup.</p>
					</div>
				</div>
				
				<div class="row">
				
					<div class="col-xs-12">
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
										<td><xsl:value-of select="id"/></td>
										
										<td>
											<xsl:choose>
												<xsl:when test="exists='false'"><span class="label label-danger">No Sensor Definition</span></xsl:when>
												<xsl:when test="overlap='true'"><span class="label label-danger">Overlap</span></xsl:when>
												<xsl:otherwise><span class="label label-success">OK</span></xsl:otherwise>
											</xsl:choose>
										</td>
										<td style="text-align:right"><xsl:value-of select="format-number(count,'###,###')"/></td>
									</tr>
								</xsl:for-each>
							</tbody>
							<tfoot style="border-top: 2px black solid; border-bottom: 2px black solid;">
								<tr >
									<td>Total</td>
									<td> </td>
									<td style="text-align:right"><xsl:value-of select="format-number(count,'###,###')"/></td>
								</tr>
							</tfoot>
						</table>
					</div>
					
					<div class="col-xs-3">
						<label>From</label>
					</div>
					<div class="col-xs-9">
						<input class="form-control" type="text" value="{$config/*/oldest}" disabled=""/> 
					</div>
					<div class="col-xs-3">
						<label>To</label>
					</div>
					<div class="col-xs-9">
						<input class="form-control" type="text" value="{$config/*/newest}" disabled=""/> 
					</div>
					
					<form id="form">
					
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
							<input type="hidden" name="fileURI" value="{$config/*/fileURI}"/>
							<input type="hidden" name="first" value="{$config/*/oldestRaw}"/>
							<input type="hidden" name="last" value="{$config/*/newestRaw}"/>
							<input type="hidden" name="action" value="confirm"/>
							
						</div>						
					</form>
					
				</div>
				
				<div class="row" style="margin-top: 1em;">
					
					<div class="col-xs-3">
						<button id="restoreButton" class="btn btn-primary">Restore</button>
					</div>
					<div class="col-xs-9" id="statusDiv" style="display:none;">
						<div class="progress" style="margin-bottom: 0">
							<div class="progress-bar" role="progressbar" style="width: 0%;" id="statusProgress"/>
						</div>
						<div id="statusMessage"/>
					</div>
				</div>
			</div>


		</div>

	</xsl:template>

</xsl:stylesheet>