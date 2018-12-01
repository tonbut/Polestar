<?xml version="1.0" encoding="UTF-8" ?>
<!-- Copyright 2015 1060 Research Ltd Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License 
	at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR 
	CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:nk="http://1060.org">
	<xsl:output method="xml" />
	<xsl:param name="config" nk:class="java.lang.String"></xsl:param>
	<xsl:template match="/*">
		<div>
			<script>
				<xsl:comment>
					$(function() {
						var intervalTimer;
						$("#downloadButton").click( function() {
							var data=$("#form").serialize();
							$("#downloadButton").attr("disabled", "disabled");
							$("#statusDiv").css("display","block");
							$.post("/polestar/sensors/backup",data);
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
									{	var msg="Backing up "+d.progress+" of "+d.progressTotal+" ("+Math.round(percent)+"%) Time remaining "+mins+"min, "+secs+"sec";
										$("#statusMessage").html(msg);
									}
									if (d.state!="BACKUP_INPROGRESS")
									{	clearInterval(intervalTimer);
										if (d.state=="BACKUP_COMPLETE")
										{	
											$("#additionalActions").css("display","block");
											
											window.location.href="/polestar/sensors/backup?action=download";
										}
									}
									
									
								},"json");
							}
						});
						$("#downloadAgain").click(function(){
							window.location.href="/polestar/sensors/backup?action=download";
						});
						$("#delete").click(function(){
							console.log("click delete");
							$.get("/polestar/sensors/backup?action=delete2");
						});
					});
				</xsl:comment>
			</script>

			<div class="container">
				<div class="row">
				
					<div class="alert alert-info">
						<h2 style="margin-top: 0;"><span class="glyphicon glyphicon-cloud-download"></span> Sensor Data Backup</h2>
						
					</div>
				
				
					<div class="col-xs-12">
						<table class="table table-condensed table-striped">
							<thead>
								<tr>
									<th>Sensor</th>
									<th style="text-align:right">Data points</th>
									<th style="text-align:right">Size (bytes)</th>
								</tr>
							</thead>
							<tbody>
								<xsl:for-each select="sensor">
									<tr>
										<td><xsl:value-of select="id"/></td>
										<td style="text-align:right"><xsl:value-of select="format-number(count,'###,###')"/></td>
										<td style="text-align:right"><xsl:value-of select="format-number(size,'###,###')"/></td>
									</tr>
								</xsl:for-each>
							</tbody>
							<tfoot style="border-top: 2px black solid; border-bottom: 2px black solid;">
								<tr >
									<td>Total</td>
									<td style="text-align:right"><xsl:value-of select="format-number(totals/count,'###,###')"/></td>
									<td style="text-align:right"><xsl:value-of select="format-number(totals/size,'###,###')"/></td>
								</tr>
							</tfoot>
						</table>
					</div>
				
					<div class="col-xs-3">
						<button id="downloadButton" class="btn btn-primary">Backup</button>
					</div>
					<div class="col-xs-9" id="statusDiv" style="display:none;">
						<div class="progress" style="margin-bottom: 0">
							<div class="progress-bar" role="progressbar" style="width: 0%;" id="statusProgress"/>
						</div>
						<div id="statusMessage"/>
						<div id="additionalActions" style="display:none;">
							<button id="downloadAgain" class="btn btn-default">Download again</button>
							<xsl:text> </xsl:text>
							<button id="delete" class="btn btn-default">Cleanup download from server</button>
						</div>
					</div>
				
				
				
					<form id="form">
						<input type="hidden" name="backupSpecification" value="{$config}"/>
						<input type="hidden" name="action" value="confirm"/>
					</form>



				</div>
			</div>


		</div>

	</xsl:template>

</xsl:stylesheet>