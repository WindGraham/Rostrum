#!/usr/bin/env python3
"""
Render Markdown with Mermaid diagrams to PDF.
"""

import re
import subprocess
import sys
import tempfile
from pathlib import Path


def extract_mermaid_blocks(content: str) -> list[tuple[str, str, int]]:
    """Extract mermaid code blocks from markdown content."""
    pattern = r'```mermaid\n(.*?)```'
    matches = re.finditer(pattern, content, re.DOTALL)
    blocks = []
    for i, match in enumerate(matches):
        blocks.append((match.group(0), match.group(1).strip(), i))
    return blocks


def render_mermaid_to_png(mermaid_code: str, output_path: Path) -> bool:
    """Render mermaid code to PNG using mermaid-cli."""
    with tempfile.NamedTemporaryFile(mode='w', suffix='.mmd', delete=False) as f:
        f.write(mermaid_code)
        temp_file = f.name
    
    # Get puppeteer config path
    script_dir = Path(__file__).parent
    puppeteer_config = script_dir / 'puppeteer-config.json'
    
    try:
        cmd = ['npx', '-y', '@mermaid-js/mermaid-cli', '-i', temp_file, '-o', str(output_path), '-b', 'transparent', '-s', '2']
        if puppeteer_config.exists():
            cmd.extend(['-p', str(puppeteer_config)])
        
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=60
        )
        if result.returncode != 0:
            print(f"Warning: mermaid render failed: {result.stderr}", file=sys.stderr)
            return False
        return True
    except subprocess.TimeoutExpired:
        print("Warning: mermaid render timed out", file=sys.stderr)
        return False
    finally:
        Path(temp_file).unlink(missing_ok=True)


def process_markdown(input_path: Path, output_dir: Path) -> str:
    """Process markdown file, render mermaid blocks to images."""
    content = input_path.read_text(encoding='utf-8')
    
    # Extract and render mermaid blocks
    blocks = extract_mermaid_blocks(content)
    
    if not blocks:
        print("No mermaid blocks found.")
        return content
    
    print(f"Found {len(blocks)} mermaid blocks.")
    
    # Render each block
    for full_match, mermaid_code, idx in blocks:
        img_name = f"mermaid_{idx}.png"
        img_path = output_dir / img_name
        
        print(f"Rendering mermaid block {idx + 1}/{len(blocks)}...")
        
        if render_mermaid_to_png(mermaid_code, img_path):
            # Replace mermaid block with image reference
            img_ref = f'![架构图 {idx + 1}]({img_path})'
            content = content.replace(full_match, img_ref)
            print(f"  -> Saved to {img_path}")
        else:
            print(f"  -> Failed, keeping original mermaid block")
    
    return content


def convert_to_pdf(markdown_content: str, output_pdf: Path, title: str = "OmniMaster"):
    """Convert markdown to PDF using pandoc."""
    with tempfile.NamedTemporaryFile(mode='w', suffix='.md', delete=False, encoding='utf-8') as f:
        f.write(markdown_content)
        temp_md = f.name
    
    try:
        cmd = [
            'pandoc',
            temp_md,
            '-o', str(output_pdf),
            '--pdf-engine=xelatex',
            '-V', 'CJKmainfont=Noto Sans CJK SC',
            '-V', 'geometry:margin=2.5cm',
            '-V', f'title={title}',
            '-V', 'colorlinks=true',
            '-V', 'linkcolor=blue',
            '--toc',
            '--toc-depth=2',
            '-f', 'markdown+implicit_figures'
        ]
        
        print(f"Running pandoc...")
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        
        if result.returncode != 0:
            print(f"Pandoc error: {result.stderr}", file=sys.stderr)
            return False
        
        print(f"PDF generated: {output_pdf}")
        return True
    except subprocess.TimeoutExpired:
        print("Pandoc timed out", file=sys.stderr)
        return False
    finally:
        Path(temp_md).unlink(missing_ok=True)


def main():
    if len(sys.argv) < 2:
        print("Usage: python render_md_to_pdf.py <input.md> [output.pdf]")
        sys.exit(1)
    
    input_path = Path(sys.argv[1])
    if not input_path.exists():
        print(f"Error: {input_path} not found")
        sys.exit(1)
    
    # Output paths
    output_dir = input_path.parent / 'rendered_images' / 'introduction'
    output_dir.mkdir(parents=True, exist_ok=True)
    
    if len(sys.argv) >= 3:
        output_pdf = Path(sys.argv[2])
    else:
        output_pdf = input_path.with_suffix('.pdf')
    
    # Also save rendered markdown
    rendered_md = input_path.with_name(input_path.stem + '_rendered.md')
    
    # Process
    print(f"Processing {input_path}...")
    processed_content = process_markdown(input_path, output_dir)
    
    # Save rendered markdown
    rendered_md.write_text(processed_content, encoding='utf-8')
    print(f"Rendered markdown saved to: {rendered_md}")
    
    # Convert to PDF
    if convert_to_pdf(processed_content, output_pdf, "OmniMaster 项目介绍"):
        print(f"\nSuccess! PDF saved to: {output_pdf}")
    else:
        print("\nFailed to generate PDF")
        sys.exit(1)


if __name__ == '__main__':
    main()
