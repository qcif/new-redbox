
package com.googlecode.fascinator.redbox.plugins.transformer.rdmp;

import java.io.ByteArrayInputStream
import java.io.File;
import java.io.IOException;
import java.io.InputStream
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.googlecode.fascinator.api.PluginDescription;
import com.googlecode.fascinator.api.PluginException;
import com.googlecode.fascinator.api.storage.DigitalObject;
import com.googlecode.fascinator.api.storage.StorageException;
import com.googlecode.fascinator.api.transformer.Transformer;
import com.googlecode.fascinator.api.transformer.TransformerException;
import com.googlecode.fascinator.common.JsonObject;
import com.googlecode.fascinator.common.JsonSimple;
import com.googlecode.fascinator.common.JsonSimpleConfig;
import com.googlecode.fascinator.common.storage.StorageUtils
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response

@GrabResolver(name='redbox', root='http://dev.redboxresearchdata.com.au/nexus/content/groups/public/')
@Grab(group='au.com.redboxresearchdata', module='json-path', version='2.4.0')
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.Configuration.Defaults;
import com.jayway.jsonpath.internal.DefaultsImpl;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;

/**
 * <p>
 * This plugin applies edit permissions to all contributors listed on the RDMP
 * </p>
 * <p>
 * <h3>Configuration</h3>
 * <p>
 * <p>
 * 
 * @author Andrew Brazzatti
 */

public class ContributorPermissionsTransformer implements Transformer {

	OkHttpClient client;
	private Configuration jsonPathConfiguration;

	/**
	 * Default portal
	 */
	protected static String DEFAULT_PORTAL = "default";

	/**
	 * Default payload
	 */
	protected static String DEFAULT_PAYLOAD = "object.tfpackage";

	/**
	 * Logger
	 */
	private static Logger log = LoggerFactory.getLogger(ContributorPermissionsTransformer.class);

	/**
	 * Json config file
	 **/
	protected JsonSimpleConfig systemConfig;

	/**
	 * Json config file
	 **/
	protected JsonSimple itemConfig;

	private String apiBaseUrl;

	/**
	 * Overridden method init to initialize
	 *
	 * @param jsonString
	 *            of configuration for transformer
	 * @throws PluginException
	 *             if fail to parse the config
	 */

	public void init(String jsonString) throws PluginException {
		try {
			systemConfig = new JsonSimpleConfig(jsonString);
			reset();
		} catch (IOException e) {
			throw new PluginException(e);
		}
	}

	/**
	 * Overridden method init to initialize
	 *
	 * @param jsonString
	 *            of configuration for transformer
	 * @throws PluginException
	 *             if fail to parse the config
	 */

	public void init(File jsonFile) throws PluginException {
		try {
			systemConfig = new JsonSimpleConfig(jsonFile);
			reset();
		} catch (IOException e) {
			throw new PluginException(e);
		}
	}

	/**
	 * Constructor
	 */
	public ContributorPermissionsTransformer() {
		// Initialise JsonPath classes for speed
		Defaults defaults = DefaultsImpl.INSTANCE;
		jsonPathConfiguration = Configuration.builder().jsonProvider(new GsonJsonProvider()).options(defaults.options())
				.build();
		JsonPath.isPathDefinite('$');

		client = new OkHttpClient();
	}

	/**
	 * Initialise the plugin, also used during subsequent executions
	 *
	 * @throws TransformerException
	 *             if errors occur
	 */
	private void reset() throws TransformerException {
		
	}

	/**
	 * Gets plugin Id
	 *
	 * @return pluginId
	 */

	public String getId() {
		return "contributorPermissions";
	}

	/**
	 * Gets plugin name
	 *
	 * @return pluginName
	 */

	public String getName() {
		return "RDMP Contributor Permissions";
	}

	/**
	 * Gets a PluginDescription object relating to this plugin.
	 *
	 * @return a PluginDescription
	 */

	public PluginDescription getPluginDetails() {
		return new PluginDescription(this);
	}

	/**
	 * Overridden shutdown method
	 *
	 * @throws PluginException
	 */

	public void shutdown() throws PluginException {
		// clean up any resources if required
	}

	/**
	 * Overridden transform method
	 *
	 * @param in
	 *            : The DigitalObject to be processed/transformer
	 * @param jsonConfig
	 *            : The configuration for this item's harvester
	 * @return processed: The DigitalObject after being transformed
	 * @throws TransformerException
	 *             if fail to transform
	 */
	public DigitalObject transform(DigitalObject inObject, String jsonConfig) throws TransformerException {
		try {
			this.log.info("Assigning permissions");
			
			JsonSimple itemConfig = new JsonSimple(jsonConfig);
			String emailProperty = itemConfig.getString("email","emailProperty");
			JSONArray editContributorProperties = itemConfig.getArray("editContributorProperties");
			JSONArray viewContributorProperties = itemConfig.getArray("viewContributorProperties");
			
			JsonSimple metadata = new JsonSimple(inObject.getPayload("metadata.tfpackage").open());

			JsonObject authorizationObject = metadata.getObject("authorization");
			JSONArray editArray = (JSONArray) authorizationObject.get("edit");

			JSONArray newEditList = new JSONArray();
			JSONArray editPendingList = new JSONArray();
			this.log.info("Edit cont properties");
			this.log.info(editContributorProperties);
			this.log.info(editContributorProperties.getClass().getName());
			for (Object object in editContributorProperties) {
				this.log.info("Processing edit contributor property :"+ object);
				String contributorProperty = (String) object;
				
				def contributorObject = getMetadataValue(metadata, contributorProperty);
				this.log.info(contributorObject.toString());
				if(contributorObject instanceof JsonArray) {
					for(contributor in contributorObject) {
					def contributorEmailObjectEmail = null;
					if(contributorObject instanceof com.google.gson.JsonObject) {
					 contributorEmailObjectEmail = ((com.google.gson.JsonObject)contributorObject).get(emailProperty);
					}
					String contributorEmail = "";
					if(contributorEmailObjectEmail != null) {
					    contributorEmail = contributorEmailObjectEmail.getAsString();
					
					
					} else if(contributorObject instanceof com.google.gson.JsonArray) {
					 	contributorEmail = ((com.google.gson.JsonArray)contributorObject).get(0);
					}
					if(!contributorEmail.equals("")){
						contributorEmail = contributorEmail.replaceAll("\"","");
						String contributorUsername = lookupUsernameInPortal(contributorEmail);
						if (contributorUsername != null) {
							newEditList.add(contributorUsername);
						} else {
							editPendingList.add(contributorEmail);
						}
					}
					}
				} else {
					String contributorEmail = "";
					if(contributorObject instanceof com.google.gson.JsonObject) {
						def contributorEmailObjectEmail = ((com.google.gson.JsonObject)contributorObject).get(emailProperty);
						if(contributorEmailObjectEmail != null) {
					    contributorEmail = contributorEmailObjectEmail.getAsString();
					    }
					} else {
						this.log.info("Contributor email is:" + contributorEmail);
						contributorEmail = contributorObject; 
					}
					this.log.info("Contributor Email is: " + contributorEmail );
					if(contributorEmail !="") {
					contributorEmail = contributorEmail.replaceAll("\"","");
					String contributorUsername = lookupUsernameInPortal(contributorEmail);
					if (contributorUsername != null) {
						this.log.info("Found username: " + contributorUsername );
						newEditList.add(contributorUsername);
					} else {
						editPendingList.add(contributorEmail);
					}
					}
					
				}
			}

			JSONArray newViewList = new JSONArray();
			JSONArray viewPendingList = new JSONArray();
			
			for (Object object in viewContributorProperties) {
				String contributorProperty = (String) object;
				def contributorObject = getMetadataValue(metadata, contributorProperty);
				if(contributorObject instanceof JsonArray) {
					for(contributor in contributorObject) {
						def contributorEmailObjectEmail = ((com.google.gson.JsonObject)contributor).get(emailProperty);
					String contributorEmail = "";
					if(contributorEmailObjectEmail != null) {
					    contributorEmail = contributorEmailObjectEmail.getAsString();
					
						String contributorUsername = lookupUsernameInPortal(contributorEmail);
						if (contributorUsername != null) {
							newViewList.add(contributorUsername);
						} else {
							viewPendingList.add(contributorEmail);
						}
					 }
					}
				} else {
					def contributorEmailObjectEmail = ((com.google.gson.JsonObject)contributorObject).get(emailProperty);
					String contributorEmail = "";
					if(contributorEmailObjectEmail != null) {
					    contributorEmail = contributorEmailObjectEmail.getAsString();
					
					String contributorUsername = lookupUsernameInPortal(contributorEmail);
					if (contributorUsername != null) {
						newViewList.add(contributorUsername);
					} else {
						viewPendingList.add(contributorEmail);
					}
					}
				}
			}
			
			JsonObject metadataJsonObject = metadata.getJsonObject();
			authorizationObject.put("edit", newEditList);
			authorizationObject.put("editPending", editPendingList);
			authorizationObject.put("view", newEditList);
			authorizationObject.put("viewPending", editPendingList);
			metadataJsonObject.put("authorization",authorizationObject);
			InputStream inputStream = new ByteArrayInputStream(new JsonSimple(metadataJsonObject).toString(true).getBytes("UTF8"));
			StorageUtils.createOrUpdatePayload(inObject, "metadata.tfpackage", inputStream);
		} catch (Exception e) {
			log.error("Contributor Permissions Transformer failed", e)
		}

		return inObject;
	}

	private Object getMetadataValue(JsonSimple metadata, String property) {
		DocumentContext triggerStreamContext = JsonPath
				.parse(new JsonParser().parse(metadata.toString()), jsonPathConfiguration);

		return triggerStreamContext.read('$.'+property);
	}

	private String lookupUsernameInPortal(String email) {
		log.debug("Looking up username for: " + email);
		String url = systemConfig.getString("http://redboxportal:1500/default/rdmp", "portalUrlBase")+"/api/users/find?searchBy=email&query=";
		String apiToken = systemConfig.getString("", "portalApiToken");
		
		Request request = new Request.Builder().url(url+email).addHeader("Content-Type", "application/json")
				.addHeader("Authorization", "Bearer "+apiToken).get().build();

		Response response;
		try {
			response = client.newCall(request).execute();

			if (response.code() != 200) {
				log.error("API call to lookup username in portal failed\n" + response.body().string());
				return null;
			}

			return new JsonSimple(response.body().string()).getString(null, "username");
		} catch (IOException e) {
			log.error("API call to lookup username in portal failed\n",e);
			return null;
		}
	}

	private List<String> getContributorEmails(JSONArray contributors) {
		List<String> contributorEmails = new ArrayList<String>();
		for (Object object in contributors) {
			JsonObject contributor = (JsonObject) object;
			String email = (String) contributor.get("email");
			if (email != null) {
				contributorEmails.add(email);
			}
		}
		return contributorEmails;
	}
}
