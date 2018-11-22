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
				
				
					<form method="POST">

						<div class="form-group">
							<input type="hidden" name="backupSpecification" value="{$config}"/>
							<input type="hidden" name="action" value="confirm"/>
							<button type="submit" class="btn btn-primary">Download backup</button>
						</div>						
						
						
					</form>



				</div>
			</div>


		</div>

	</xsl:template>

</xsl:stylesheet>