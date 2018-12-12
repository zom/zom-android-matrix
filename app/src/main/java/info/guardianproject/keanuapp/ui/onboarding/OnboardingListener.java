package info.guardianproject.keanuapp.ui.onboarding;

public interface OnboardingListener {

    public void registrationSuccessful (OnboardingAccount account);

    public void registrationFailed (String err);

}
