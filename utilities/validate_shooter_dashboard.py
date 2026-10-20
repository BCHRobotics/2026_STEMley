#!/usr/bin/env python3
"""Static checks for the Elastic 2026 shooter dashboard; no robot connection."""
import json
import math
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
LAYOUT = ROOT / 'dashboards/elastic/shooter-tuning.json'


def validate(path=LAYOUT):
    data = json.loads(Path(path).read_text())
    assert data['version'] == 1
    assert data['grid_size'] == 32
    assert [t['name'] for t in data['tabs']] == ['Shooter Tuning', 'Motor Health']
    shooter = (ROOT / 'src/main/java/frc/robot/subsystems/Shooter.java').read_text()
    container = (ROOT / 'src/main/java/frc/robot/RobotContainer.java').read_text()
    smart = {'/SmartDashboard/' + s for s in re.findall(r'SmartDashboard\.put(?:Number|Boolean)\("([^"]+)"', shooter)}
    logged = {'/AdvantageKit/RealOutputs/' + s for s in re.findall(r'Logger\.recordOutput\("([^"]+)"', shooter)}
    tuning = {'/SmartDashboard/Shooter/Tuning/' + s for s in re.findall(r'publishDefaultNumber\("([^"]+)"', shooter)}
    tuning.add('/SmartDashboard/Shooter/Tuning/UseDistanceForTargetRPM')
    commands = {'/SmartDashboard/Shooter/Tuning/' + name for name in ['Run Flywheel', 'Stop Flywheel']}
    for name in commands:
        assert name.removeprefix('/SmartDashboard/') in container
    all_widgets = []
    for tab in data['tabs']:
        grid = tab['grid_layout']
        assert grid['layouts'] == []
        widgets = grid['containers']
        assert len(widgets) == 18
        for i, widget in enumerate(widgets):
            assert set(widget) == {'title', 'x', 'y', 'width', 'height', 'type', 'properties'}
            for key in ['x', 'y', 'width', 'height']:
                assert type(widget[key]) is float and math.isfinite(widget[key])
                assert widget[key] % 32 == 0
            assert widget['x'] >= 0 and widget['y'] >= 0
            assert widget['width'] >= 128 and widget['height'] >= 96
            assert widget['x'] + widget['width'] <= 1280
            assert widget['y'] + widget['height'] <= 608
            props = widget['properties']
            topic = props['topic']
            assert topic in smart | logged | tuning | commands, topic
            assert props['period'] == 0.05
            if widget['type'] == 'Command':
                assert topic in commands and props['show_type'] is False
            else:
                assert props['data_type'] == ('boolean' if widget['type'] in ['Toggle Switch', 'Boolean Box'] else 'double')
            if widget['type'] == 'Text Display':
                assert topic in tuning and props['show_submit_button'] is True
            elif widget['type'] == 'Graph':
                assert type(props['time_displayed']) is float
                assert props['time_displayed'] == 10.0
                assert type(props['color']) is int
            else:
                assert widget['type'] in ['Command', 'Toggle Switch', 'Boolean Box', 'Large Text Display']
            for other in widgets[:i]:
                overlap = (widget['x'] < other['x'] + other['width'] and
                           other['x'] < widget['x'] + widget['width'] and
                           widget['y'] < other['y'] + other['height'] and
                           other['y'] < widget['y'] + widget['height'])
                assert not overlap, (widget['title'], other['title'])
        all_widgets.extend(widgets)
    topics = {w['properties']['topic'] for w in all_widgets}
    assert tuning | commands <= topics
    for motor in range(1, 5):
        assert f'/SmartDashboard/Shooter/Shooter{motor}RPM' in topics
        assert f'/AdvantageKit/RealOutputs/Shooter/Motor{motor}CurrentAmps' in topics
    return {tab['name']: len(tab['grid_layout']['containers']) for tab in data['tabs']}


class LayoutTest(unittest.TestCase):
    def test_import_shape_topics_types_and_geometry(self):
        print(validate())


if __name__ == '__main__':
    unittest.main()
