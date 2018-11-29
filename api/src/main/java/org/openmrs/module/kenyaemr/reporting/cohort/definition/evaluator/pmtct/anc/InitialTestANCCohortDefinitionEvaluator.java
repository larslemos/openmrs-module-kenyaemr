package org.openmrs.module.kenyaemr.reporting.cohort.definition.evaluator.pmtct.anc;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Cohort;
import org.openmrs.annotation.Handler;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyaemr.reporting.EmrReportingUtils;
import org.openmrs.module.kenyaemr.reporting.cohort.definition.pmtct.anc.InitialTestANCCohortDefinition;
import org.openmrs.module.kenyaemr.reporting.cohort.definition.pmtct.anc.KnownPositivesFirstANCCohortDefinition;
import org.openmrs.module.kenyaemr.reporting.library.ETLReports.MOH731Greencard.ETLMoh731GreenCardCohortLibrary;
import org.openmrs.module.reporting.cohort.EvaluatedCohort;
import org.openmrs.module.reporting.cohort.definition.CohortDefinition;
import org.openmrs.module.reporting.cohort.definition.evaluator.CohortDefinitionEvaluator;
import org.openmrs.module.reporting.cohort.definition.service.CohortDefinitionService;
import org.openmrs.module.reporting.common.ObjectUtil;
import org.openmrs.module.reporting.evaluation.EvaluationContext;
import org.openmrs.module.reporting.evaluation.EvaluationException;
import org.openmrs.module.reporting.evaluation.querybuilder.SqlQueryBuilder;
import org.openmrs.module.reporting.evaluation.service.EvaluationService;
import org.openmrs.module.reporting.query.encounter.EncounterQueryResult;
import org.openmrs.module.reporting.query.encounter.definition.EncounterQuery;
import org.openmrs.module.reporting.query.encounter.evaluator.EncounterQueryEvaluator;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Evaluator for patients initial test at ANC
 */
@Handler(supports = {InitialTestANCCohortDefinition.class})
public class InitialTestANCCohortDefinitionEvaluator implements CohortDefinitionEvaluator {

	private final Log log = LogFactory.getLog(this.getClass());
	@Autowired
	private ETLMoh731GreenCardCohortLibrary moh731GreencardCohorts;
	@Autowired
	EvaluationService evaluationService;
	@Override
	public EvaluatedCohort evaluate(CohortDefinition cohortDefinition, EvaluationContext context) throws EvaluationException {

		InitialTestANCCohortDefinition definition = (InitialTestANCCohortDefinition) cohortDefinition;
		if (definition == null)
			return null;

		String qry =  "select distinct v.patient_id  from kenyaemr_etl.etl_mch_antenatal_visit v\n" +
				"                                    left outer join kenyaemr_etl.etl_mch_enrollment e on e.patient_id= v.patient_id\n" +
				"                                    left outer join kenyaemr_etl.etl_mchs_delivery ld on ld.patient_id= v.patient_id\n" +
				"                                    left outer join kenyaemr_etl.etl_mch_postnatal_visit p on p.patient_id=ld.patient_id\n" +
				"                                where e.hiv_status !=703 and\n" +
				"                                      ld.final_test_result is null and\n" +
				"                                       p.final_test_result is null and\n" +
				"                                       v.final_test_result is not null ;";

		Cohort newCohort = new Cohort();
		SqlQueryBuilder builder = new SqlQueryBuilder();
		builder.append(qry);
		Date startDate = (Date)context.getParameterValue("startDate");
		Date endDate = (Date)context.getParameterValue("endDate");
		builder.addParameter("endDate", endDate);
		builder.addParameter("startDate", startDate);
		List<Integer> ptIds = evaluationService.evaluateToList(builder, Integer.class, context);

		newCohort.setMemberIds(new HashSet<Integer>(ptIds));


		return new EvaluatedCohort(newCohort, definition, context);
	}

}



