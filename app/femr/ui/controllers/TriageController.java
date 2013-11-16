package femr.ui.controllers;

import com.google.inject.Inject;
import com.google.inject.Provider;
import femr.business.dtos.CurrentUser;
import femr.business.dtos.ServiceResponse;
import femr.business.services.ISearchService;
import femr.business.services.ISessionService;
import femr.business.services.ITriageService;
import femr.common.models.IPatient;
import femr.common.models.IPatientEncounter;
import femr.common.models.IPatientEncounterVital;
import femr.common.models.IVital;
import femr.ui.models.triage.CreateViewModelGet;
import femr.ui.models.triage.CreateViewModelPost;
import femr.ui.helpers.controller.TriageHelper;
import femr.ui.views.html.triage.index;
import femr.util.calculations.dateUtils;
import femr.util.stringhelpers.StringUtils;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TriageController extends Controller {

    private final Form<CreateViewModelPost> createViewModelForm = Form.form(CreateViewModelPost.class);
    private ITriageService triageService;
    private ISessionService sessionService;
    private Provider<IPatient> patientProvider;
    private ISearchService searchService;
    private Provider<IPatientEncounter> patientEncounterProvider;
    private Provider<IPatientEncounterVital> patientEncounterVitalProvider;
    private TriageHelper triageHelper;

    @Inject
    public TriageController(ITriageService triageService, ISessionService sessionService, ISearchService searchService, Provider<IPatient> patientProvider, Provider<IPatientEncounter> patientEncounterProvider, Provider<IPatientEncounterVital> patientEncounterVitalProvider) {

        this.triageService = triageService;
        this.sessionService = sessionService;
        this.searchService = searchService;
        this.patientProvider = patientProvider;
        this.patientEncounterProvider = patientEncounterProvider;
        this.patientEncounterVitalProvider = patientEncounterVitalProvider;
    }

    public Result createGet() {
        boolean error = false;
        ServiceResponse<List<? extends IVital>> vitalServiceResponse = searchService.findAllVitals();
        if (vitalServiceResponse.hasErrors()){
            error = true;
        }
        List<? extends IVital> vitalNames = vitalServiceResponse.getResponseObject();

        CurrentUser currentUser = sessionService.getCurrentUserSession();

        IPatient patient = patientProvider.get();
        patient.setId(0);
        return ok(index.render(currentUser, vitalNames, error, patient, null));
    }

    /*
    *if id is 0 then it is a new patient
    * if id is > 0 then it is a new encounter
     */
    public Result createPost(int id) {

        CreateViewModelPost viewModel = createViewModelForm.bindFromRequest().get();

        CurrentUser currentUser = sessionService.getCurrentUserSession();

        ServiceResponse<IPatient> patientServiceResponse;
        if (id == 0){
            IPatient patient = triageHelper.createPatient(viewModel, currentUser);
            patientServiceResponse = triageService.createPatient(patient);
        }
        else{
            patientServiceResponse = searchService.findPatientById(id);
        }

        if (patientServiceResponse.hasErrors()){
            //error
        }


        IPatientEncounter patientEncounter = triageHelper.createPatientEncounter(viewModel, currentUser, patientServiceResponse.getResponseObject());
        ServiceResponse<IPatientEncounter> patientEncounterServiceResponse = triageService.createPatientEncounter(patientEncounter);

        List<IPatientEncounterVital> patientEncounterVitals = populatePatientEncounterVitals(viewModel, patientEncounterServiceResponse, currentUser);

        for (int i = 0; i < patientEncounterVitals.size(); i++) {
            if (patientEncounterVitals.get(i).getVitalValue() > 0) {
                triageService.createPatientEncounterVital(patientEncounterVitals.get(i));
            }
        }

        return redirect("/show?id=" + patientServiceResponse.getResponseObject().getId());
    }

    /*
    Used when user is creating an encounter for an existing patient.
     */
    public Result createPopulatedGet() {

        boolean error = false;
        String s_id = request().getQueryString("id");

        CreateViewModelGet viewModelGet = new CreateViewModelGet();

        ServiceResponse<List<? extends IVital>> vitalServiceResponse = searchService.findAllVitals();
        if (vitalServiceResponse.hasErrors()){
            error = true;
        }
        List<? extends IVital> vitalNames = vitalServiceResponse.getResponseObject();

        CurrentUser currentUser = sessionService.getCurrentUserSession();

        if (StringUtils.isNullOrWhiteSpace(s_id)) {
            error = true;
            return ok(index.render(currentUser, vitalNames, error, patientProvider.get(), null));
        }
        s_id = s_id.trim();
        Integer id = Integer.parseInt(s_id);
        ServiceResponse<IPatient> patientServiceResponse = searchService.findPatientById(id);

        if (patientServiceResponse.hasErrors()) {
            error = true;
            return ok(index.render(currentUser, vitalNames, error, patientProvider.get(), null));
        } else {
            IPatient patient = patientServiceResponse.getResponseObject();
            // populate viewmodelget
            viewModelGet.setFirstName(patient.getFirstName());
            viewModelGet.setLastName(patient.getLastName());
            viewModelGet.setBirth(patient.getAge());
            viewModelGet.setAge(dateUtils.calculateYears(patient.getAge()));
            viewModelGet.setSex(patient.getSex());
            viewModelGet.setAddress(patient.getAddress());
            viewModelGet.setCity(patient.getCity());
            return ok(index.render(currentUser, vitalNames, false,patient,  viewModelGet));
        }
    }


    //helper functions


    private List<IPatientEncounterVital> populatePatientEncounterVitals(CreateViewModelPost viewModel,
                                                                        ServiceResponse<IPatientEncounter> patientEncounterServiceResponse,
                                                                        CurrentUser currentUser) {

        List<IPatientEncounterVital> patientEncounterVitals = new ArrayList<>();
        IPatientEncounterVital[] patientEncounterVital = new IPatientEncounterVital[9];
        for (int i = 0; i < 9; i++) {
            patientEncounterVital[i] = patientEncounterVitalProvider.get();
            patientEncounterVital[i].setDateTaken((dateUtils.getCurrentDateTime()));
            patientEncounterVital[i].setUserId(currentUser.getId());
            patientEncounterVital[i].setPatientEncounterId(patientEncounterServiceResponse.getResponseObject().getId());
            patientEncounterVital[i].setVitalId(i + 1);
        }

        //Respiratory Rate
        if (viewModel.getRespiratoryRate() == null) {
            patientEncounterVital[0].setVitalValue(-1);
        } else {
            patientEncounterVital[0].setVitalValue(viewModel.getRespiratoryRate().floatValue());
        }

        //Heart Rate
        if (viewModel.getHeartRate() == null) {
            patientEncounterVital[1].setVitalValue(-1);
        } else {
            patientEncounterVital[1].setVitalValue(viewModel.getHeartRate().floatValue());
        }

        //Temperature
        if (viewModel.getTemperature() == null) {
            patientEncounterVital[2].setVitalValue(-1);
        } else {
            patientEncounterVital[2].setVitalValue(viewModel.getTemperature().floatValue());
        }

        //Oxygen Saturation
        if (viewModel.getOxygenSaturation() == null) {
            patientEncounterVital[3].setVitalValue(-1);
        } else {
            patientEncounterVital[3].setVitalValue(viewModel.getOxygenSaturation().floatValue());
        }

        //Height - Feet
        if (viewModel.getHeightFeet() == null) {
            patientEncounterVital[4].setVitalValue(-1);
        } else {
            patientEncounterVital[4].setVitalValue(viewModel.getHeightFeet().floatValue());
        }

        //Height - Inches
        if (viewModel.getHeightInches() == null) {
            patientEncounterVital[5].setVitalValue(-1);
        } else {
            patientEncounterVital[5].setVitalValue(viewModel.getHeightInches().floatValue());
        }

        //Weight
        if (viewModel.getWeight() == null) {
            patientEncounterVital[6].setVitalValue(-1);
        } else {
            patientEncounterVital[6].setVitalValue(viewModel.getWeight().floatValue());
        }

        //Blood Pressure - Systolic
        if (viewModel.getBloodPressureSystolic() == null) {
            patientEncounterVital[7].setVitalValue(-1);
        } else {
            patientEncounterVital[7].setVitalValue(viewModel.getBloodPressureSystolic().floatValue());
        }

        //Blood Pressure - Diastolic
        if (viewModel.getBloodPressureDiastolic() == null) {
            patientEncounterVital[8].setVitalValue(-1);
        } else {
            patientEncounterVital[8].setVitalValue(viewModel.getBloodPressureDiastolic().floatValue());
        }

        patientEncounterVitals.addAll(Arrays.asList(patientEncounterVital));
        return patientEncounterVitals;
    }
}
