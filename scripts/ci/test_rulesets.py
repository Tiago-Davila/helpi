import unittest
import configure_rulesets


class RulesetsTest(unittest.TestCase):
    def test_protected_branches_have_no_bypass_and_require_all_gates(self):
        for branch in ('develop', 'main'):
            rule = configure_rulesets.ruleset(branch)
            self.assertEqual([], rule['bypass_actors'])
            self.assertEqual([f'refs/heads/{branch}'], rule['conditions']['ref_name']['include'])
            rules = {r['type']: r.get('parameters') for r in rule['rules']}
            self.assertIn('deletion', rules)
            self.assertIn('non_fast_forward', rules)
            self.assertEqual(0, rules['pull_request']['required_approving_review_count'])
            self.assertTrue(rules['required_status_checks']['strict_required_status_checks_policy'])
            checks = {c['context'] for c in rules['required_status_checks']['required_status_checks']}
            self.assertEqual(set(configure_rulesets.CHECKS), checks)
            self.assertEqual('high_or_higher', rules['code_scanning']['code_scanning_tools'][0]['security_alerts_threshold'])
            self.assertIn('merge', rules['pull_request']['allowed_merge_methods'])
