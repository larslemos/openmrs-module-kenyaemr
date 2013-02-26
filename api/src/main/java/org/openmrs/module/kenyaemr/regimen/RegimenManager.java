/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.kenyaemr.regimen;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Concept;
import org.openmrs.DrugOrder;
import org.openmrs.api.context.Context;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Manager for regimens
 */
public class RegimenManager {

	private static final String REGIMENS_FILENAME = "regimens.xml";

	private static Map<String, Integer> masterSetConcepts = new LinkedHashMap<String, Integer>();

	private static Map<String, Map<String, DrugReference>> drugs = new LinkedHashMap<String, Map<String, DrugReference>>();

	private static Map<String, List<RegimenDefinitionGroup>> regimenGroups = new LinkedHashMap<String, List<RegimenDefinitionGroup>>();

	private static int definitionsVersion = 0;

	/**
	 * Gets the category codes
	 * @return the category codes
	 */
	public static Set<String> getCategoryCodes() {
		return masterSetConcepts.keySet();
	}

	/**
	 * Gets the master set concept for the given category
	 * @param category the category, e.g. "ARV"
	 * @return the concept
	 */
	public static Concept getMasterSetConcept(String category) {
		Integer conceptId = masterSetConcepts.get(category);
		return conceptId != null ? Context.getConceptService().getConcept(conceptId) : null;
	}

	/**
	 * Gets the individual drugs for the given category
	 * @param category the category, e.g. "ARV"
	 * @return the concept ids
	 */
	public static Collection<DrugReference> getDrugs(String category) {
		return drugs.get(category).values();
	}

	/**
	 * Gets the regimen groups for the given category
	 * @param category the category, e.g. "ARV"
	 * @return the regimen groups
	 */
	public static List<RegimenDefinitionGroup> getRegimenGroups(String category) {
		return regimenGroups.get(category);
	}

	/**
	 * Gets the version number of the definitions (from XML)
	 * @return the version number
	 */
	public static int getDefinitionsVersion() {
		return definitionsVersion;
	}

	/**
	 * Finds definitions that match the given regimen
	 * @param category the category, e.g. "ARV"
	 * @param regimenOrder the regimen
	 * @param exact whether matches must be exact (includes dose, units and frequency)
	 * @return the definitions
	 */
	public static List<RegimenDefinition> findDefinitions(String category, RegimenOrder regimenOrder, boolean exact) {
		List<RegimenDefinitionGroup> groups = regimenGroups.get(category);
		if (groups == null) {
			throw new IllegalArgumentException("No such category: " + category);
		}

		List<RegimenDefinition> matches = new ArrayList<RegimenDefinition>();

		for (RegimenDefinitionGroup group : groups) {
			outer:
			for (RegimenDefinition definition : group.getRegimens()) {
				List<RegimenComponent> regimen = definition.getComponents();
				Set<DrugOrder> orders = regimenOrder.getDrugOrders();

				// Skip if regimen doesn't have same number of orders
				if (regimen.size() != orders.size()) {
					continue;
				}

				// Check each component has an equivalent drug order
				for (RegimenComponent component : regimen) {

					// Does regimen have a drug order for this component?
					boolean regimenHasComponent = false;
					for (DrugOrder order : orders) {
						DrugReference componentDrugRef = component.getDrugRef();
						DrugReference orderDrugRef = DrugReference.fromDrugOrder(order);

						if (componentDrugRef.equals(orderDrugRef)) {

							if (!exact || (ObjectUtils.equals(order.getDose(), component.getDose()) && StringUtils.equals(order.getUnits(), component.getUnits()) && StringUtils.equals(order.getFrequency(), component.getFrequency()))) {
								regimenHasComponent = true;
								break;
							}
						}
					}

					if (!regimenHasComponent) {
						continue outer;
					}
				}

				// Regimen has all components of the definition
				matches.add(definition);
			}
		}

		return matches;
	}

	/**
	 * Setup the standard regimens from XML
	 * @throws Exception if error occurs
	 */
	public static void setupStandardRegimens() throws Exception {
		try {
			InputStream stream = RegimenManager.class.getClassLoader().getResourceAsStream(REGIMENS_FILENAME);

			loadDefinitionsFromXML(stream);
		}
		catch (IOException ex) {
			throw new RuntimeException("Cannot find " + REGIMENS_FILENAME + ". Make sure it's in api/src/main/resources/metadata");
		}
	}

	/**
	 * Loads definitions from an input stream containing XML
	 * @param stream the path to XML resource
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws SAXException
	 */
	public static void loadDefinitionsFromXML(InputStream stream) throws ParserConfigurationException, IOException, SAXException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = dbFactory.newDocumentBuilder();

		Document document = builder.parse(stream);

		Element root = document.getDocumentElement();
		definitionsVersion = Integer.parseInt(root.getAttribute("version"));

		masterSetConcepts.clear();
		drugs.clear();
		regimenGroups.clear();

		// Parse each category
		NodeList categoryNodes = root.getElementsByTagName("category");
		for (int c = 0; c < categoryNodes.getLength(); c++) {
			Element categoryElement = (Element)categoryNodes.item(c);
			String categoryCode = categoryElement.getAttribute("code");
			String masterSetUuid = categoryElement.getAttribute("masterSetUuid");

			Concept masterSetConcept = Context.getConceptService().getConceptByUuid(masterSetUuid);
			masterSetConcepts.put(categoryCode, masterSetConcept.getConceptId());

			Map<String, DrugReference> categoryDrugs = new HashMap<String, DrugReference>();
			List<RegimenDefinitionGroup> categoryGroups = new ArrayList<RegimenDefinitionGroup>();

			// Parse all drug concepts for this category
			NodeList drugNodes = categoryElement.getElementsByTagName("drug");
			for (int d = 0; d < drugNodes.getLength(); d++) {
				Element drugElement = (Element)drugNodes.item(d);
				String drugCode = drugElement.getAttribute("code");
				String drugConceptUuid = drugElement.hasAttribute("conceptUuid") ? drugElement.getAttribute("conceptUuid") : null;
				String drugDrugUuid = drugElement.hasAttribute("drugUuid") ? drugElement.getAttribute("drugUuid") : null;

				DrugReference drug = (drugDrugUuid != null) ? DrugReference.fromDrugUuid(drugDrugUuid) : DrugReference.fromConceptUuid(drugConceptUuid);

				categoryDrugs.put(drugCode, drug);
			}

			// Parse all groups for this category
			NodeList groupNodes = categoryElement.getElementsByTagName("group");
			for (int g = 0; g < groupNodes.getLength(); g++) {
				Element groupElement = (Element)groupNodes.item(g);
				String groupCode = groupElement.getAttribute("code");
				String groupName = groupElement.getAttribute("name");

			   	RegimenDefinitionGroup group = new RegimenDefinitionGroup(groupCode, groupName);
				categoryGroups.add(group);

				// Parse all regimen definitions for this group
				NodeList regimenNodes = groupElement.getElementsByTagName("regimen");
				for (int r = 0; r < regimenNodes.getLength(); r++) {
					Element regimenElement = (Element)regimenNodes.item(r);
					String name = regimenElement.getAttribute("name");

					RegimenDefinition regimenDefinition = new RegimenDefinition(name, group);

					// Parse all components for this regimen
					NodeList componentNodes = regimenElement.getElementsByTagName("component");
					for (int p = 0; p < componentNodes.getLength(); p++) {
						Element componentElement = (Element)componentNodes.item(p);
						String drugCode = componentElement.getAttribute("drugCode");
						Double dose = componentElement.hasAttribute("dose") ? Double.parseDouble(componentElement.getAttribute("dose")) : null;
						String units = componentElement.hasAttribute("units") ? componentElement.getAttribute("units") : null;
						String frequency = componentElement.hasAttribute("frequency") ? componentElement.getAttribute("frequency") : null;

						DrugReference drug = categoryDrugs.get(drugCode);
						if (drug == null)
							throw new RuntimeException("Regimen component references invalid drug: " + drugCode);

						regimenDefinition.addComponent(drug, dose, units, frequency);
					}

					group.addRegimen(regimenDefinition);
				}
			}

			drugs.put(categoryCode, categoryDrugs);
			regimenGroups.put(categoryCode, categoryGroups);
		}
	}
}