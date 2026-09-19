import pandas as pd
from sklearn.model_selection import GroupShuffleSplit
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, confusion_matrix

CSV_PATH = "data/processed/pothole_dataset.csv"
TEST_SIZE = 0.25
RANDOM_STATE = 42


def main():

    # STEP 1 — Load dataset
    df = pd.read_csv(CSV_PATH)

    print(f"Loaded {len(df)} rows from {CSV_PATH}")

    print("\nOriginal class counts:")
    print(df["type"].value_counts())


    # STEP 2 — Create 3-class label
    #
    # 0 = Pothole
    # 1 = Speed Bump
    # 2 = Uneven Road
    #
    # Metal_bumps, Ditch and Manhole_cover
    # are grouped into Uneven Road.

    def create_label(event_type):

        if event_type == "Pothole":
            return 0

        elif event_type == "Speed_bump":
            return 1

        else:
            return 2


    df["label"] = df["type"].apply(create_label)


    print("\nNew 3-class counts:")
    print(
        df["label"]
        .map({
            0: "Pothole",
            1: "Speed Bump",
            2: "Uneven Road"
        })
        .value_counts()
    )


    # STEP 3 — Split by event to avoid leakage
    splitter = GroupShuffleSplit(
        n_splits=1,
        test_size=TEST_SIZE,
        random_state=RANDOM_STATE
    )

    train_idx, test_idx = next(
        splitter.split(
            df,
            groups=df["event"]
        )
    )

    train_df = df.iloc[train_idx].copy()
    test_df = df.iloc[test_idx].copy()

    print(
        f"\nTrain events: {train_df['event'].nunique()} "
        f"({len(train_df)} samples)"
    )

    print(
        f"Test events:  {test_df['event'].nunique()} "
        f"({len(test_df)} samples)"
    )


    # STEP 4 — Pick feature columns
    feature_cols = [
        c for c in df.columns
        if c.endswith((
            "_mean",
            "_std",
            "_min",
            "_max",
            "_ptp",
            "_rms"
        ))
    ] + ["has_gyro", "n_readings"]


    # STEP 5 — Create train/test data
    X_train = train_df[feature_cols].fillna(0)
    y_train = train_df["label"]

    X_test = test_df[feature_cols].fillna(0)
    y_test = test_df["label"]


    # STEP 6 — Train Random Forest
    model = RandomForestClassifier(
        n_estimators=200,
        class_weight="balanced",
        random_state=RANDOM_STATE
    )

    model.fit(
        X_train,
        y_train
    )


    # STEP 7 — Evaluate
    preds = model.predict(X_test)

    print("\n=== Classification report (test set) ===")

    print(
        classification_report(
            y_test,
            preds,
            labels=[0, 1, 2],
            target_names=[
                "pothole",
                "speed_bump",
                "uneven_road"
            ],
            zero_division=0
        )
    )


    print("=== Confusion matrix ===")

    print(
        confusion_matrix(
            y_test,
            preds,
            labels=[0, 1, 2]
        )
    )


    # STEP 8 — Analyze errors
    test_df = test_df.copy()
    test_df["pred"] = preds


    print("\n=== Misclassifications ===")

    errors = test_df[
        test_df["label"] != test_df["pred"]
    ]


    if len(errors) > 0:

        print(
            errors[
                ["type", "label", "pred"]
            ].value_counts()
        )

    else:

        print("No misclassifications!")


    # STEP 9 — Feature importance
    importances = pd.Series(
        model.feature_importances_,
        index=feature_cols
    )

    print("\n=== Top 10 most important features ===")

    print(
        importances
        .sort_values(ascending=False)
        .head(10)
    )


if __name__ == "__main__":
    main()
