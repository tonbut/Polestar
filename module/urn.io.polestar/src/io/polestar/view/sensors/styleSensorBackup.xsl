<?xml version="1.0" encoding="UTF-8" ?>
<!-- Copyright 2015 1060 Research Ltd Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License 
	at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR 
	CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:nk="http://1060.org">
	<xsl:output method="xml" />
	<xsl:param name="config"/>
	<xsl:template match="/">
		<div>
			<!-- https://www.jqueryscript.net/form/Two-side-Multi-Select-Plugin-with-jQuery-multiselect-js.html -->
			<!-- https://github.com/uxsolutions/bootstrap-datepicker -->
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
					});
				</xsl:comment>
			</script>

			<div class="container">
				<div class="row">
				
					<div class="alert alert-info">
						<h2 style="margin-top: 0;"><span class="glyphicon glyphicon-cloud-download"></span> Sensor Data Backup</h2>
						Select the sensors that you want to backup. Then select the date range -
						this will default to the complete history from the first record to last.
						Finally click the backup button.
						This will check the backup and provide some statistics for the backup
						before prompting you to confirm.
					</div>
				

					<form method="POST">

						<div class="form-group">
							<label class="control-label" for="multiselect">Sensors</label>
							<div class="row">

								<div class="col-xs-5">
									<select name="from" id="multiselect" class="form-control" size="8" multiple="multiple">
										<xsl:for-each select="/sensors/sensor">
											<option value="{id}">
												<xsl:value-of select="name" />
												(
												<xsl:value-of select="id" />
												)
											</option>
										</xsl:for-each>
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
									<select name="to" id="multiselect_to" class="form-control" size="8" multiple="multiple">_
									</select>
								</div>

							</div>
						</div>
						
						<div class="form-group">
							<label class="control-label" for="from">Date Range</label>
							<div class="row">
								<div class="col-xs-12">
								<div class="input-daterange input-group" id="datepicker">
								    <input type="text" class="input-sm form-control" name="start" value="{$config/root/start}"/>
								    <span class="input-group-addon">to</span>
								    <input type="text" class="input-sm form-control" name="end" value="{$config/root/end}"/>
								</div>
								</div>
							</div>
						</div>
						
						<div class="form-group">
							<input type="hidden" name="action" value="form"/>
							<button type="submit" class="btn btn-primary">Backup...</button>
						</div>						
						
						
					</form>



				</div>
			</div>


		</div>

	</xsl:template>

</xsl:stylesheet>