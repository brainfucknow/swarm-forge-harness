from swarmforge_pipeline import catalogue

ROLES_CONF = """\
# comment
specifier frontier subagent
falsifier strong   subagent gate
spec-judge strong  subagent spec-judge
coder     strong   subagent
security-reviewer strong subagent optional
qa        strong   subagent
"""

TIERS_CONF = """\
frontier inherit high   gpt-6.1-sol   inherit mistral-large-latest
strong   opus    medium gpt-6.1-sol   inherit mistral-medium-latest
fast     sonnet  low    gpt-6.1-terra inherit mistral-small-latest
"""

ROUTES_CONF = """\
feature  specifier,coder,qa
spike    specifier
"""


def test_load_roles_parses_flags():
    roles = catalogue.load_roles(ROLES_CONF)
    assert [r.name for r in roles] == [
        "specifier", "falsifier", "spec-judge", "coder", "security-reviewer", "qa",
    ]
    assert roles[0].tier == "frontier" and roles[0].runs_as == "subagent"
    assert roles[1].is_gate and not roles[1].is_spec_judge
    assert roles[2].is_spec_judge
    assert "optional" in roles[4].flags


def test_gates_spec_judge_stages():
    roles = catalogue.load_roles(ROLES_CONF)
    assert catalogue.gates(roles) == ["falsifier"]
    assert catalogue.spec_judge(roles) == "spec-judge"
    assert catalogue.stages(roles) == ["specifier", "coder", "security-reviewer", "qa"]


def test_mistral_model_uses_vibe_column_and_tier_default():
    tiers = catalogue.load_tiers(TIERS_CONF)
    assert catalogue.mistral_model(tiers, "frontier") == "mistral-large-latest"
    assert catalogue.mistral_model(tiers, "strong") == "mistral-medium-latest"
    assert catalogue.mistral_model(tiers, "fast") == "mistral-small-latest"
    tiers["custom"] = {"vibe": "inherit"}
    assert catalogue.mistral_model(tiers, "custom") == "mistral-medium-latest"
    tiers["other"] = {"vibe": "mistral-large-4"}
    assert catalogue.mistral_model(tiers, "other") == "mistral-large-4"


def test_load_routes():
    assert catalogue.load_routes(ROUTES_CONF) == {
        "feature": ["specifier", "coder", "qa"],
        "spike": ["specifier"],
    }
